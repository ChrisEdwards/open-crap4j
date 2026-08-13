#!/usr/bin/env python3
"""Parse JaCoCo XML reports and compute per-method CRAP scores."""
import sys
import xml.etree.ElementTree as ET
import re
from collections import defaultdict

LAMBDA_RE = re.compile(r'^lambda\$(.+)\$\d+$')


def counter_dict(elem):
    """Return {type: (missed, covered)} for the counters directly under elem."""
    out = {}
    for c in elem.findall('counter'):
        out[c.get('type')] = (int(c.get('missed')), int(c.get('covered')))
    return out


def parse_report(path, module_name):
    tree = ET.parse(path)
    root = tree.getroot()

    # Overall report-level counters (whole module)
    overall = counter_dict(root)

    methods = []  # list of dicts, one per real (folded) method

    for pkg in root.findall('package'):
        for cls in pkg.findall('class'):
            class_name = cls.get('name')
            # raw per-method counters, keyed by method name
            raw_methods = {}
            for m in cls.findall('method'):
                mname = m.get('name')
                if mname == '<clinit>':
                    continue
                counters = counter_dict(m)
                raw_methods[mname] = counters

            # Fold lambda$NAME$N into NAME
            folded = defaultdict(lambda: {'INSTRUCTION': [0, 0], 'BRANCH': [0, 0], 'COMPLEXITY': [0, 0]})
            real_names = set()

            for mname, counters in raw_methods.items():
                lam = LAMBDA_RE.match(mname)
                target = lam.group(1) if lam else mname
                if not lam:
                    real_names.add(mname)
                for ctype in ('INSTRUCTION', 'BRANCH', 'COMPLEXITY'):
                    if ctype in counters:
                        missed, covered = counters[ctype]
                        folded[target][ctype][0] += missed
                        folded[target][ctype][1] += covered

            # Only emit methods that either are real methods themselves,
            # or lambdas whose target real method exists in this class.
            # (lambdas targeting methods not directly seen, e.g. static init
            # helpers, are still folded under their target name if present)
            for name, counters in folded.items():
                # "static" is a reserved word and can never be a real Java
                # method name, so a folded group named "static" only exists
                # because of a lambda$static$N synthetic method owned by a
                # static initializer (<clinit>). Skip it, consistent with
                # skipping <clinit> itself.
                if name == 'static':
                    continue
                # Skip if this "name" is itself only a lambda target that
                # doesn't correspond to any real method entry AND wasn't a
                # <clinit> (already filtered) - keep it anyway since lambda
                # targets are legitimate method names (e.g. constructors,
                # regular methods) even if the base method had no direct
                # bytecode entry (rare). We still require some complexity data.
                comp_missed, comp_covered = counters['COMPLEXITY']
                if comp_missed == 0 and comp_covered == 0:
                    continue
                cc = comp_missed + comp_covered

                branch_missed, branch_covered = counters['BRANCH']
                instr_missed, instr_covered = counters['INSTRUCTION']

                has_branch = (branch_missed + branch_covered) > 0
                if has_branch:
                    cov = branch_covered / (branch_missed + branch_covered)
                    cov_kind = 'branch'
                else:
                    total_instr = instr_missed + instr_covered
                    cov = (instr_covered / total_instr) if total_instr > 0 else 1.0
                    cov_kind = 'instruction'

                crap = (cc ** 2) * ((1 - cov) ** 3) + cc

                methods.append({
                    'module': module_name,
                    'class': class_name,
                    'method': name,
                    'cc': cc,
                    'coverage': cov,
                    'cov_kind': cov_kind,
                    'crap': crap,
                })

    return overall, methods


def pct(missed, covered):
    total = missed + covered
    if total == 0:
        return None
    return 100.0 * covered / total


def main():
    reports = [
        ('contrast-mcp-core', 'contrast-mcp-core/build/reports/jacoco/test/jacocoTestReport.xml'),
        ('contrast-mcp-stdio-app', 'contrast-mcp-stdio-app/build/reports/jacoco/test/jacocoTestReport.xml'),
    ]

    all_methods = []
    print('=== Per-module overall coverage ===')
    for module_name, path in reports:
        try:
            overall, methods = parse_report(path, module_name)
        except FileNotFoundError:
            print(f'{module_name}: report not found at {path}')
            continue
        all_methods.extend(methods)

        line = overall.get('LINE', (0, 0))
        instr = overall.get('INSTRUCTION', (0, 0))
        branch = overall.get('BRANCH', (0, 0))

        print(f'-- {module_name} --')
        lp = pct(*line)
        ip = pct(*instr)
        bp = pct(*branch)
        print(f'  LINE coverage:        {lp:.2f}%' if lp is not None else '  LINE coverage: n/a')
        print(f'  INSTRUCTION coverage: {ip:.2f}%' if ip is not None else '  INSTRUCTION coverage: n/a')
        print(f'  BRANCH coverage:      {bp:.2f}%' if bp is not None else '  BRANCH coverage: n/a')
        print(f'  Methods analyzed (folded): {sum(1 for mm in methods)}')

    print()
    print(f'=== Total methods analyzed: {len(all_methods)} ===')

    # Complexity distribution
    buckets = {'1-3': 0, '4-6': 0, '7-10': 0, '11-15': 0, '16+': 0}
    for mm in all_methods:
        cc = mm['cc']
        if cc <= 3:
            buckets['1-3'] += 1
        elif cc <= 6:
            buckets['4-6'] += 1
        elif cc <= 10:
            buckets['7-10'] += 1
        elif cc <= 15:
            buckets['11-15'] += 1
        else:
            buckets['16+'] += 1

    print('=== Complexity distribution ===')
    for k, v in buckets.items():
        print(f'  cc {k}: {v}')

    crap_gt_15 = sum(1 for mm in all_methods if mm['crap'] > 15)
    crap_gt_30 = sum(1 for mm in all_methods if mm['crap'] > 30)
    cc_gt_15 = sum(1 for mm in all_methods if mm['cc'] > 15)

    print()
    print(f'CRAP > 15: {crap_gt_15}')
    print(f'CRAP > 30: {crap_gt_30}')
    print(f'cc > 15 (over cap): {cc_gt_15}')

    # Top 15 by CRAP
    top15 = sorted(all_methods, key=lambda mm: mm['crap'], reverse=True)[:15]
    print()
    print('=== Top 15 methods by CRAP ===')
    print('class | method | cc | coverage% | kind | CRAP')
    for mm in top15:
        short_class = mm['class'].split('/')[-1]
        print(f"{short_class} | {mm['method']} | {mm['cc']} | {mm['coverage']*100:.1f} | {mm['cov_kind']} | {mm['crap']:.2f}")

    # cc >= 4 with branch coverage < 50%
    count = 0
    for mm in all_methods:
        if mm['cc'] >= 4 and mm['cov_kind'] == 'branch' and mm['coverage'] < 0.5:
            count += 1
    print()
    print(f'Methods with cc >= 4 and branch coverage < 50%: {count}')


if __name__ == '__main__':
    main()

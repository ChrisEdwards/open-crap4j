#!/usr/bin/env python3
"""Tests for crap-metrics.py — guards against overload-collapsing and lambda-folding regressions."""
import os
import tempfile
import pytest

import sys
sys.path.insert(0, os.path.dirname(__file__))

from importlib import import_module
cm = import_module('crap-metrics')
parse_report = cm.parse_report


def _write_xml(xml_str):
    f = tempfile.NamedTemporaryFile(mode='w', suffix='.xml', delete=False)
    f.write(xml_str)
    f.close()
    return f.name


def _method_xml(name, desc, cc_missed, cc_covered, br_missed=0, br_covered=0,
                instr_missed=0, instr_covered=1):
    return (
        f'<method name="{name}" desc="{desc}">'
        f'<counter type="COMPLEXITY" missed="{cc_missed}" covered="{cc_covered}"/>'
        f'<counter type="BRANCH" missed="{br_missed}" covered="{br_covered}"/>'
        f'<counter type="INSTRUCTION" missed="{instr_missed}" covered="{instr_covered}"/>'
        f'</method>'
    )


def _wrap_report(methods_xml):
    return (
        '<report name="test">'
        '<counter type="LINE" missed="0" covered="1"/>'
        '<package name="com/example">'
        f'<class name="com/example/Foo">{methods_xml}</class>'
        '</package>'
        '</report>'
    )


class TestOverloadedMethods:
    def test_parse_should_preserveBothOverloads_when_sameNameDifferentDesc(self):
        xml = _wrap_report(
            _method_xml('process', '(I)V', 0, 2)
            + _method_xml('process', '(Ljava/lang/String;)V', 0, 3)
        )
        path = _write_xml(xml)
        try:
            _, methods = parse_report(path, 'test')
            process_methods = [m for m in methods if m['method'] == 'process']
            assert len(process_methods) == 2, (
                f"Expected 2 overloads of 'process', got {len(process_methods)}"
            )
        finally:
            os.unlink(path)

    def test_parse_should_assignDistinctDescriptors_when_overloaded(self):
        xml = _wrap_report(
            _method_xml('process', '(I)V', 0, 2)
            + _method_xml('process', '(Ljava/lang/String;)V', 0, 3)
        )
        path = _write_xml(xml)
        try:
            _, methods = parse_report(path, 'test')
            descs = {m['desc'] for m in methods if m['method'] == 'process'}
            assert descs == {'(I)V', '(Ljava/lang/String;)V'}
        finally:
            os.unlink(path)

    def test_parse_should_computeIndependentCrap_when_overloadsHaveDifferentComplexity(self):
        xml = _wrap_report(
            _method_xml('process', '(I)V', 0, 1, instr_missed=0, instr_covered=10)
            + _method_xml('process', '(Ljava/lang/String;)V', 5, 5, br_missed=5, br_covered=5)
        )
        path = _write_xml(xml)
        try:
            _, methods = parse_report(path, 'test')
            by_desc = {m['desc']: m for m in methods if m['method'] == 'process'}

            simple = by_desc['(I)V']
            assert simple['cc'] == 1
            assert simple['cov_kind'] == 'instruction'

            complex_m = by_desc['(Ljava/lang/String;)V']
            assert complex_m['cc'] == 10
            assert complex_m['cov_kind'] == 'branch'
        finally:
            os.unlink(path)


class TestLambdaFolding:
    def test_parse_should_foldLambdaIntoHost_when_singleMethodMatchesTarget(self):
        xml = _wrap_report(
            _method_xml('apply', '(Ljava/lang/Object;)V', 0, 2)
            + _method_xml('lambda$apply$0', '(Ljava/lang/Object;)V', 0, 1)
        )
        path = _write_xml(xml)
        try:
            _, methods = parse_report(path, 'test')
            assert len(methods) == 1
            assert methods[0]['method'] == 'apply'
            assert methods[0]['cc'] == 3
        finally:
            os.unlink(path)

    def test_parse_should_foldLambdaIntoFirstOverload_when_targetIsOverloaded(self):
        xml = _wrap_report(
            _method_xml('apply', '(I)V', 0, 2)
            + _method_xml('apply', '(Ljava/lang/String;)V', 0, 3)
            + _method_xml('lambda$apply$0', '(Ljava/lang/Object;)V', 0, 1)
        )
        path = _write_xml(xml)
        try:
            _, methods = parse_report(path, 'test')
            apply_methods = [m for m in methods if m['method'] == 'apply']
            assert len(apply_methods) == 2
            total_cc = sum(m['cc'] for m in apply_methods)
            assert total_cc == 6
        finally:
            os.unlink(path)


class TestStaticLambdaSkip:
    def test_parse_should_skipStaticLambda_when_clinitOwnsIt(self):
        xml = _wrap_report(
            _method_xml('doWork', '()V', 0, 1)
            + _method_xml('lambda$static$0', '()V', 0, 1)
        )
        path = _write_xml(xml)
        try:
            _, methods = parse_report(path, 'test')
            names = [m['method'] for m in methods]
            assert 'static' not in names
            assert 'doWork' in names
        finally:
            os.unlink(path)


class TestCrapScore:
    def test_parse_should_returnCcAsCrap_when_fullyCovered(self):
        xml = _wrap_report(
            _method_xml('simple', '()V', 0, 3, instr_missed=0, instr_covered=10)
        )
        path = _write_xml(xml)
        try:
            _, methods = parse_report(path, 'test')
            assert len(methods) == 1
            assert methods[0]['crap'] == pytest.approx(3.0)
        finally:
            os.unlink(path)

    def test_parse_should_returnCcSquaredPlusCc_when_zeroCoverage(self):
        xml = _wrap_report(
            _method_xml('uncovered', '()V', 4, 0, instr_missed=10, instr_covered=0)
        )
        path = _write_xml(xml)
        try:
            _, methods = parse_report(path, 'test')
            assert len(methods) == 1
            assert methods[0]['crap'] == pytest.approx(4**2 + 4)
        finally:
            os.unlink(path)

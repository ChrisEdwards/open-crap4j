# JaCoCo XML fixtures

Clean-room fixtures behind research/lambda-folding-spec.md. Generated 2026-08-12
with Temurin OpenJDK 21.0.5 and JaCoCo 0.8.12 (agent plus CLI from Maven
Central) from the sources in `src/`.

- **report.xml** — full run over all fixture classes. Covers every lambda
  naming case in the spec's edge table: constructors, field and static
  initializers, overloads, nested lambdas, interface default and static
  methods, record compact constructor, anonymous class, enum.
- **nd.xml** — same classes compiled with `-g:none`. No `line` attributes
  anywhere. Exercises the overload fallback to document order and the
  omitted-line report field.
- **enum.xml** — enum-only report. Shows JaCoCo's own filtering, only `go`,
  `lambda$go$0`, and `<clinit>` appear, never `values`/`valueOf`/`$values`.

All three open with the real JaCoCo DOCTYPE line
(`<!DOCTYPE report PUBLIC "-//JACOCO//DTD Report 1.1//EN" "report.dtd">`).
Parser tests must accept this header with external entity resolution disabled.
XXE hardening that rejects every DOCTYPE rejects every real report.

To regenerate: compile `src/` (once plain, once with `-g:none`), run the
mains under the JaCoCo agent, then `java -jar jacococli.jar report ... --xml`.

// Run from the backend directory with Groovy 3 and a full JDK 17+.
def formatter = new File('config/spotless/record-annotations.groovy')
def format = { String input ->
    new GroovyShell(new Binding([source: input])).evaluate(formatter)
}

def cases = [
    [name: 'record headers only, including nested annotations and Unicode', input: '''
@First @Second class Container {
    @First @Second String field;
    @First @Second void handle(@First @Second String value) {}
    record Request<T>(
            @Schema(description = "Tên, @ và (dấu ngoặc)", tags = {"a", "b"}) @Valid java.util.List<T> values,
            @Valid String... names) {
        static final String TEXT = "@Valid String untouched";
        @First @Second void accept(@First @Second String value) {}
    }
}
''', expected: '''
@First @Second class Container {
    @First @Second String field;
    @First @Second void handle(@First @Second String value) {}
    record Request<T>(
            @Schema(description = "Tên, @ và (dấu ngoặc)", tags = {"a", "b"})
            @Valid
            java.util.List<T> values,
            @Valid
            String... names) {
        static final String TEXT = "@Valid String untouched";
        @First @Second void accept(@First @Second String value) {}
    }
}
'''],
    [name: 'comments between annotations are preserved', input: '''
record Request(
        @First /* preserve this comment */ @Second String value) {}
''', expected: '''
record Request(
        @First /* preserve this comment */ @Second
        String value) {}
'''],
    [name: 'record text in a comment is not a declaration', input: '''
// record Fake(@First String value)
class Ordinary { void accept(@First @Second String value) {} }
''', expected: '''
// record Fake(@First String value)
class Ordinary { void accept(@First @Second String value) {} }
''']
]

cases.each { testCase ->
    String actual = format(testCase.input)
    assert actual == testCase.expected: testCase.name
    assert format(actual) == actual: "Not idempotent: ${testCase.name}"
}
try {
    format('record Broken(')
    throw new AssertionError('Malformed record must fail rather than be rewritten')
} catch (IllegalArgumentException expected) {
    // Parsing failure is the intended behavior.
}
println 'Record annotation checks passed: 3 layout cases, idempotence, and malformed input.'

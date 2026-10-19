import com.sun.source.tree.ClassTree
import com.sun.source.tree.Tree
import com.sun.source.tree.VariableTree
import com.sun.source.util.JavacTask
import com.sun.source.util.TreeScanner
import com.sun.source.util.Trees
import javax.lang.model.element.Modifier
import javax.tools.Diagnostic
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.SimpleJavaFileObject
import javax.tools.ToolProvider

// Eclipse shares annotation settings between record components and parameters.
// Use the Java syntax tree to change whitespace only in record component headers.
String input = source
if (!(input =~ /\brecord\s+\w+/).find()) {
    return input
}

def compiler = ToolProvider.systemJavaCompiler
if (compiler == null) {
    throw new IllegalStateException('Record annotation formatting requires a JDK, not a JRE')
}
def diagnostics = new DiagnosticCollector<JavaFileObject>()
def fileManager = compiler.getStandardFileManager(diagnostics, null, null)
def file = new SimpleJavaFileObject(URI.create('string:///FormattingInput.java'), JavaFileObject.Kind.SOURCE) {
    @Override
    CharSequence getCharContent(boolean ignoreEncodingErrors) {
        return input
    }
}
def edits = []
try {
    JavacTask task = (JavacTask) compiler.getTask(null, fileManager, diagnostics,
            ['-proc:none', '--release', '17'], null, [file])
    def unit = task.parse().iterator().next()
    if (diagnostics.diagnostics.any { it.kind == Diagnostic.Kind.ERROR }) {
        throw new IllegalArgumentException('Cannot parse Java source for record annotation formatting')
    }
    def positions = Trees.instance(task).sourcePositions
    new TreeScanner<Void, Void>() {
        @Override
        Void visitClass(ClassTree declaration, Void unused) {
            if (declaration.kind == Tree.Kind.RECORD) {
                // Records cannot declare additional instance fields in their body.
                declaration.members.findAll {
                    it instanceof VariableTree && !it.modifiers.flags.contains(Modifier.STATIC)
                }.each { VariableTree component ->
                    def annotations = component.modifiers.annotations
                    if (!annotations.isEmpty()) {
                        int start = (int) positions.getStartPosition(unit, component)
                        int lineStart = input.lastIndexOf('\n', start) + 1
                        String indent = (input.substring(lineStart, start) =~ /^[ \t]*/)[0]
                        annotations.eachWithIndex { annotation, index ->
                            int end = (int) positions.getEndPosition(unit, annotation)
                            Tree next = index + 1 < annotations.size() ? annotations[index + 1] : component.type
                            int nextStart = (int) positions.getStartPosition(unit, next)
                            // Preserve comments between annotations and the declaration.
                            if (end >= 0 && nextStart >= end && input.substring(end, nextStart).trim().isEmpty()) {
                                edits.add([start: end, end: nextStart, text: '\n' + indent])
                            }
                        }
                    }
                }
            }
            return super.visitClass(declaration, unused)
        }
    }.scan(unit, null)
} finally {
    fileManager.close()
}
def result = new StringBuilder(input)
edits.sort { a, b -> b.start <=> a.start }.each { edit ->
    result.replace(edit.start as int, edit.end as int, edit.text as String)
}
return result.toString()

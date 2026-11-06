const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const android = path.resolve(__dirname, '..');
const shared = path.join(android, 'design/icons');
const manifest = JSON.parse(fs.readFileSync(path.join(shared, 'icon-manifest.json'), 'utf8'));
const aliases = JSON.parse(fs.readFileSync(path.join(shared, 'android-aliases.json'), 'utf8'));
const vectors = {};
for (const [name, value] of Object.entries(manifest.symbols)) {
  const svg = fs.readFileSync(path.join(shared, value.path), 'utf8');
  const paths = [...svg.matchAll(/<path\b([^>]*?)\/?\s*>/g)].map(match => {
    const d = /\bd="([^"]+)"/.exec(match[1]);
    if (!d) throw new Error('Missing path in ' + name);
    return { data:d[1], evenOdd:/fill-rule="evenodd"/.test(match[1]) };
  });
  if (!paths.length || /<(rect|circle|ellipse|polygon|polyline|line|g)\b/.test(svg)) throw new Error('Unsupported SVG shape: ' + name);
  vectors[name] = paths;
}
for (const name of Object.values(aliases)) if (!vectors[name]) throw new Error('Unmapped Android semantic: '+name);
const lines = [
'package com.kairos.app.ui.icons', '',
'import androidx.compose.ui.graphics.Color',
'import androidx.compose.ui.graphics.PathFillType',
'import androidx.compose.ui.graphics.SolidColor',
'import androidx.compose.ui.graphics.vector.ImageVector',
'import androidx.compose.ui.graphics.vector.PathParser',
'import androidx.compose.ui.unit.dp',
'import java.util.concurrent.ConcurrentHashMap', '',
'/** Generated from design/icons; the iOS catalog uses the identical MIT-licensed SVG paths. */',
'object KairosIcons {',
'    private data class Shape(val data: String, val evenOdd: Boolean = false)',
'    private val cache = ConcurrentHashMap<String, ImageVector>()',
'    private val paths: Map<String, List<Shape>> = mapOf('
];
for (const [name, shapes] of Object.entries(vectors)) lines.push(`        ${JSON.stringify(name)} to listOf(${shapes.map(s=>`Shape(${JSON.stringify(s.data)}, ${s.evenOdd})`).join(', ')}),`);
lines.push('    )', '',
'    fun symbol(name: String): ImageVector = cache.getOrPut(name) {',
'        val shapes = requireNotNull(paths[name]) { "Unmapped Kairos icon: $name" }',
'        ImageVector.Builder(',
'            name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 256f, viewportHeight = 256f,',
'            autoMirror = name in setOf("chevron.left", "chevron.right", "arrow.turn.down.right")',
'        ).apply {',
'            shapes.forEach { shape ->',
'                addPath(',
'                    pathData = PathParser().parsePathString(shape.data).toNodes(),',
'                    fill = SolidColor(Color.Black),',
'                    pathFillType = if (shape.evenOdd) PathFillType.EvenOdd else PathFillType.NonZero',
'                )',
'            }',
'        }.build()',
'    }', '');
for (const [name, symbol] of Object.entries(aliases)) lines.push(`    val ${name}: ImageVector get() = symbol(${JSON.stringify(symbol)})`);
lines.push('}', '');
const target=path.join(android,'app/src/main/java/com/kairos/app/ui/icons/KairosIcons.kt');
fs.mkdirSync(path.dirname(target),{recursive:true});
if(process.argv.includes('--check')) {
  require('assert/strict').equal(fs.readFileSync(target,'utf8'), lines.join('\n'), 'Generated Kotlin differs from shared source');
} else fs.writeFileSync(target,lines.join('\n'));
console.log('PASS: Android vectors use the shared SVG path data.');

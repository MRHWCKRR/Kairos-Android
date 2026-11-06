const fs=require('fs'),path=require('path'),crypto=require('crypto'),assert=require('assert/strict');
const root=path.resolve(__dirname,'..'),icons=path.join(root,'design/icons');
const manifest=JSON.parse(fs.readFileSync(path.join(icons,'icon-manifest.json'),'utf8'));
for(const value of Object.values(manifest.symbols)){
 const bytes=fs.readFileSync(path.join(icons,value.path));
 assert.equal(crypto.createHash('sha256').update(bytes).digest('hex'),manifest.assets[value.slug].sha256);
}
function files(dir){return fs.readdirSync(dir,{withFileTypes:true}).flatMap(e=>e.isDirectory()?files(path.join(dir,e.name)):[path.join(dir,e.name)]);}
for(const f of files(path.join(root,'app/src')).filter(f=>f.endsWith('.kt'))){
 assert(!/import androidx\.compose\.material\.icons/.test(fs.readFileSync(f,'utf8')),'Platform icon import remains: '+f);
}
require('child_process').execFileSync(process.execPath,[path.join(__dirname,'generate-icons.cjs'),'--check'],{stdio:'inherit'});
console.log('PASS: shared icon checksums and Android call-site coverage.');

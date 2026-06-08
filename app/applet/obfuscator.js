const fs = require('fs');
const path = require('path');

function walk(dir) {
    let results = [];
    const list = fs.readdirSync(dir);
    list.forEach(file => {
        file = path.join(dir, file);
        const stat = fs.statSync(file);
        if (stat && stat.isDirectory()) {
            results = results.concat(walk(file));
        } else if (file.endsWith('.kt')) {
            results.push(file);
        }
    });
    return results;
}

const files = walk('app/src/main/java');

files.forEach(file => {
    let content = fs.readFileSync(file, 'utf8');
    let modified = false;

    // Use negative lookbehind/lookahead to avoid replacing inside Obfuscator.o("...") or imports
    content = content.replace(/(?<!\bo\()"(KittySpy|KittySpace|KittyDumper|KittySpyMenuService|com\.kittyspace.*?|KittyDumperEngine)"/g, (match, p1) => {
        modified = true;
        let xored = Buffer.from(p1).map(b => b ^ 0x77);
        return `com.kittyspace.ui.Obfuscator.o("${xored.toString('base64')}")`;
    });

    if (modified) {
        fs.writeFileSync(file, content);
        console.log("Obfuscated strings in " + file);
    }
});

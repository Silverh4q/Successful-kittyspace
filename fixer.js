const fs = require('fs');
const path = require('path');

function walk(dir) {
    let r = [];
    const files = fs.readdirSync(dir);
    files.forEach(f => {
        const p = path.join(dir, f);
        if (fs.statSync(p).isDirectory()) {
            r = r.concat(walk(p));
        } else if (p.endsWith('.kt')) {
            r.push(p);
        }
    });
    return r;
}

const files = walk('app/src/main/java');
files.forEach(f => {
    let c = fs.readFileSync(f, 'utf8');
    
    // Fix messed up backtick replacements, where \`Text" was written
    // Wait, let's just reset using git!
    
});

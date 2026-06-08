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

function obfStr(s) {
    let xored = Buffer.from(s).map(b => b ^ 0x77);
    return `com.kittyspace.ui.Obfuscator.o("${xored.toString('base64')}")`;
}

files.forEach(file => {
    let content = fs.readFileSync(file, 'utf8');
    let modified = false;

    // Toast
    content = content.replace(/Toast\.makeText\([^,]+,\s*"([^"]+)"/g, (match, p1) => {
        if(p1.includes("kittyspace") || p1.includes("Obfuscator") || match.includes("Obfuscator")) return match;
        modified = true;
        return match.replace(`"${p1}"`, obfStr(p1));
    });
    
    // .text = "..."
    content = content.replace(/\.text\s*=\s*"([^"]+)"/g, (match, p1) => {
        if(p1.includes("kittyspace") || p1.includes("Obfuscator") || match.includes("Obfuscator")) return match;
        modified = true;
        return match.replace(`"${p1}"`, obfStr(p1));
    });
    
    // Text("...")
    content = content.replace(/Text\s*\(\s*"([^"]+)"/g, (match, p1) => {
        if(p1.includes("kittyspace") || p1.includes("Obfuscator") || match.includes("Obfuscator")) return match;
        modified = true;
        return match.replace(`"${p1}"`, obfStr(p1));
    });
    
    // placeholder = { Text("...") } is caught above
    
    // Log.d
    content = content.replace(/Log\.d\([^,]+,\s*"([^"]+)"/g, (match, p1) => {
        if(p1.includes("kittyspace") || p1.includes("Obfuscator") || match.includes("Obfuscator")) return match;
        modified = true;
        return match.replace(`"${p1}"`, obfStr(p1));
    });

    if (modified) {
        fs.writeFileSync(file, content);
        console.log("Obfuscated advanced strings in " + file);
    }
});

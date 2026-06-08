const fs = require('fs');
const path = require('path');

function walk(dir) {
    let results = [];
    const list = fs.readdirSync(dir);
    list.forEach(file => {
        const fullPath = path.join(dir, file);
        const stat = fs.statSync(fullPath);
        if (stat && stat.isDirectory()) {
            results = results.concat(walk(fullPath));
        } else if (file.endsWith('.kt')) {
            results.push(fullPath);
        }
    });
    return results;
}

const files = walk('app/src/main/java');

function obfStr(s) {
    const x = Buffer.from(s).map(b => b ^ 0x77);
    return `com.kittyspace.ui.Obfuscator.o("${x.toString('base64')}")`;
}

files.forEach(file => {
    let c = fs.readFileSync(file, 'utf8');
    let m = false;

    const rToast = /Toast\.makeText\([^,]+,\s*"([^"\n]+)"/g;
    let match;
    while ((match = rToast.exec(c)) !== null) {
        if (!match[1].includes('Obfuscator')) {
            c = c.replace(`"${match[1]}"`, obfStr(match[1]));
            m = true;
        }
    }

    const rText = /\.text\s*=\s*"([^"\n]+)"/g;
    while ((match = rText.exec(c)) !== null) {
        if (!match[1].includes('Obfuscator')) {
            c = c.replace(`"${match[1]}"`, obfStr(match[1]));
            m = true;
        }
    }

    const rTextUI = /Text\s*\(\s*"([^"\n]+)"/g;
    while ((match = rTextUI.exec(c)) !== null) {
        if (!match[1].includes('Obfuscator')) {
            c = c.replace(`"${match[1]}"`, obfStr(match[1]));
            m = true;
        }
    }

    if (m) {
        fs.writeFileSync(file, c);
        console.log('Obfuscated ' + file);
    }
});

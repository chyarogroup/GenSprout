import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const projectDir = __dirname;

function getPomInfo() {
    const pomPath = path.join(projectDir, 'pom.xml');
    if (!fs.existsSync(pomPath)) {
        console.error('[ERROR] pom.xml not found in project directory.');
        process.exit(1);
    }
    const content = fs.readFileSync(pomPath, 'utf-8');
    const match = content.match(/<version>(.*?)<\/version>/);
    if (!match) {
        console.error('[ERROR] Could not extract version from pom.xml.');
        process.exit(1);
    }
    return match[1].trim();
}

function calculateNewVersion(currentVersion, mode) {
    const regex = /^(\d+)\.(\d+)\.(\d+)(?:-(beta|alpha)(\d+))?$/i;
    const match = currentVersion.match(regex);

    if (!match) {
        console.error(`[ERROR] Invalid semver string: ${currentVersion}`);
        process.exit(1);
    }

    let major = parseInt(match[1], 10);
    let minor = parseInt(match[2], 10);
    let patch = parseInt(match[3], 10);
    let tag = match[4] ? match[4].toLowerCase() : null;
    let betaNum = match[5] ? parseInt(match[5], 10) : null;

    const normalizedMode = mode.toLowerCase();

    if (normalizedMode === 'beta') {
        if (tag === 'beta' && betaNum !== null) {
            return `${major}.${minor}.${patch}-beta${betaNum + 1}`;
        }
        return `${major}.${minor + 1}.0-beta1`;
    } else if (normalizedMode === 'release') {
        if (tag !== null) {
            return `${major}.${minor}.${patch}`;
        }
        return `${major}.${minor + 1}.0`;
    } else if (normalizedMode === 'minorrelease' || normalizedMode === 'minor') {
        return `${major}.${minor}.${patch + 1}`;
    } else {
        console.error(`[ERROR] Unknown mode: '${mode}'. Expected 'beta', 'release', or 'minorrelease'.`);
        process.exit(1);
    }
}

function main() {
    const args = process.argv.slice(2);
    const mode = args[0];

    if (!mode || ['--help', '-h', 'help'].includes(mode)) {
        console.log('Usage: node newversion.js <beta|release|minorrelease>');
        console.log('');
        console.log('Options:');
        console.log('  beta          Bumps beta number (e.g. 1.2.0-beta2 -> 1.2.0-beta3)');
        console.log('  release       Converts beta to full release (e.g. 1.2.0-beta2 -> 1.2.0)');
        console.log('  minorrelease  Bumps patch version for release without betas (e.g. 1.2.0-beta2 -> 1.2.1)');
        process.exit(0);
    }

    const currentVersion = getPomInfo();
    const newVersion = calculateNewVersion(currentVersion, mode);

    console.log(`==============================================`);
    console.log(` Updating GenSprout Version: ${currentVersion} -> ${newVersion}`);
    console.log(` Mode: ${mode}`);
    console.log(`==============================================`);

    const pomPath = path.join(projectDir, 'pom.xml');
    let pomContent = fs.readFileSync(pomPath, 'utf-8');
    pomContent = pomContent.replace(`<version>${currentVersion}</version>`, `<version>${newVersion}</version>`);
    fs.writeFileSync(pomPath, pomContent, 'utf-8');
    console.log(`[UPDATED] pom.xml -> ${newVersion}`);

    const pluginYmlPath = path.join(projectDir, 'src', 'main', 'resources', 'paper-plugin.yml');
    if (fs.existsSync(pluginYmlPath)) {
        let pluginYmlContent = fs.readFileSync(pluginYmlPath, 'utf-8');
        pluginYmlContent = pluginYmlContent.replace(`version: ${currentVersion}`, `version: ${newVersion}`);
        fs.writeFileSync(pluginYmlPath, pluginYmlContent, 'utf-8');
        console.log(`[UPDATED] paper-plugin.yml -> ${newVersion}`);
    }

    const infoPath = path.join(projectDir, 'info.md');
    if (fs.existsSync(infoPath)) {
        let infoContent = fs.readFileSync(infoPath, 'utf-8');
        infoContent = infoContent.replaceAll(currentVersion, newVersion);
        fs.writeFileSync(infoPath, infoContent, 'utf-8');
        console.log(`[UPDATED] info.md -> ${newVersion}`);
    }

    const changelogPath = path.join(projectDir, 'CHANGELOG.md');
    if (fs.existsSync(changelogPath)) {
        let changelogContent = fs.readFileSync(changelogPath, 'utf-8');
        const versionHeader = `## Version ${newVersion}`;
        if (!changelogContent.includes(versionHeader)) {
            const splitKey = '# GenSprout Changelog';
            const template = `\n\n${versionHeader}\n* **Update**: Description of features and changes in version ${newVersion}.\n`;
            if (changelogContent.includes(splitKey)) {
                changelogContent = changelogContent.replace(splitKey, `${splitKey}${template}`);
            } else {
                changelogContent = `${versionHeader}\n* **Update**: Description of features and changes in version ${newVersion}.\n\n${changelogContent}`;
            }
            fs.writeFileSync(changelogPath, changelogContent, 'utf-8');
            console.log(`[UPDATED] CHANGELOG.md -> Added section for ${newVersion}`);
        } else {
            console.log(`[SKIP] CHANGELOG.md already contains section for ${newVersion}`);
        }
    }

    console.log(`\n==============================================`);
    console.log(` Successfully bumped version to ${newVersion}`);
    console.log(`==============================================`);
}

main();

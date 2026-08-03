import fs from 'fs';
import path from 'path';
import { execSync } from 'child_process';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// 1. Load .env file if present
function loadEnv() {
    const envPath = path.join(process.cwd(), '.env');
    if (fs.existsSync(envPath)) {
        const lines = fs.readFileSync(envPath, 'utf-8').split(/\r?\n/);
        for (const line of lines) {
            const trimmed = line.trim();
            if (!trimmed || trimmed.startsWith('#')) continue;
            const idx = trimmed.indexOf('=');
            if (idx > 0) {
                const key = trimmed.slice(0, idx).trim();
                const val = trimmed.slice(idx + 1).trim().replace(/^["']|["']$/g, '');
                if (!process.env[key]) {
                    process.env[key] = val;
                }
            }
        }
    }
}

loadEnv();

// 2. Parse pom.xml for artifactId and version
function getPomInfo() {
    const pomPath = path.join(process.cwd(), 'pom.xml');
    if (!fs.existsSync(pomPath)) {
        console.error('[ERROR] pom.xml not found in current working directory.');
        process.exit(1);
    }
    const content = fs.readFileSync(pomPath, 'utf-8');
    const artifactMatch = content.match(/<artifactId>(.*?)<\/artifactId>/);
    const versionMatch = content.match(/<version>(.*?)<\/version>/);
    const artifactId = artifactMatch ? artifactMatch[1].trim() : 'GenSprout';
    const version = versionMatch ? versionMatch[1].trim() : '1.2.0-beta1';
    return { artifactId, version };
}

// 3. Extract version notes from CHANGELOG.md
function getChangelogNotes(version) {
    const changelogPath = path.join(process.cwd(), 'CHANGELOG.md');
    if (!fs.existsSync(changelogPath)) {
        return `Release v${version}`;
    }
    const content = fs.readFileSync(changelogPath, 'utf-8');
    const sections = content.split(/^## /m);
    for (const sec of sections) {
        if (sec.startsWith(`Version ${version}`) || sec.startsWith(`v${version}`)) {
            return '## ' + sec.trim();
        }
    }
    return `Release v${version}`;
}

async function main() {
    const { artifactId, version } = getPomInfo();
    const changelog = getChangelogNotes(version);
    const isBeta = version.toLowerCase().includes('beta') || version.toLowerCase().includes('alpha');

    console.log(`==============================================`);
    console.log(` Publishing ${artifactId} v${version}`);
    console.log(`==============================================`);

    // Step 1: Maven Build
    console.log('\n[STEP 1/4] Building latest shaded JAR with Maven...');
    try {
        execSync('mvn clean package', { stdio: 'inherit' });
    } catch (e) {
        console.error('[ERROR] Maven build failed.');
        process.exit(1);
    }

    const jarName = `${artifactId}-${version}-shaded.jar`;
    let jarPath = path.join(process.cwd(), 'target', jarName);
    if (!fs.existsSync(jarPath)) {
        const unshadedName = `${artifactId}-${version}.jar`;
        jarPath = path.join(process.cwd(), 'target', unshadedName);
    }

    if (!fs.existsSync(jarPath)) {
        console.error(`[ERROR] Target JAR file not found at ${jarPath}`);
        process.exit(1);
    }

    const jarBuffer = fs.readFileSync(jarPath);
    console.log(`[INFO] Found target artifact: ${path.basename(jarPath)} (${(jarBuffer.length / 1024 / 1024).toFixed(2)} MB)`);

    // Step 2: Git Commit & Push
    console.log('\n[STEP 2/4] Pushing changes to GitHub repository...');
    try {
        execSync('git add .', { stdio: 'inherit' });
        try {
            execSync(`git commit -m "release: v${version}"`, { stdio: 'inherit' });
        } catch {
            console.log('[INFO] No uncommitted changes detected.');
        }
        execSync('git push origin main', { stdio: 'inherit' });
        console.log('[SUCCESS] Git repository updated.');
    } catch (err) {
        console.warn('[WARNING] Git push encountered an issue:', err.message);
    }

    // Step 3: GitHub Release
    console.log('\n[STEP 3/4] Publishing to GitHub Releases...');
    const githubToken = process.env.GITHUB_TOKEN;
    const githubOwner = process.env.GITHUB_OWNER || 'chyarogroup';
    const githubRepo = process.env.GITHUB_REPO || artifactId;

    if (!githubToken) {
        console.log('[SKIP] GITHUB_TOKEN not set in environment or .env file.');
    } else {
        try {
            const tag = `v${version}`;
            const releaseUrl = `https://api.github.com/repos/${githubOwner}/${githubRepo}/releases`;
            const releaseRes = await fetch(releaseUrl, {
                method: 'POST',
                headers: {
                    'Authorization': `Bearer ${githubToken}`,
                    'Accept': 'application/vnd.github+json',
                    'Content-Type': 'application/json',
                    'User-Agent': 'Node-Publisher'
                },
                body: JSON.stringify({
                    tag_name: tag,
                    name: `v${version}`,
                    body: changelog,
                    draft: false,
                    prerelease: isBeta
                })
            });

            if (!releaseRes.ok) {
                const errText = await releaseRes.text();
                console.error(`[ERROR] GitHub Release creation failed (${releaseRes.status}): ${errText}`);
            } else {
                const releaseData = await releaseRes.json();
                const uploadUrlTemplate = releaseData.upload_url;
                const uploadUrl = uploadUrlTemplate.replace(/\{\?name,label\}/, `?name=${encodeURIComponent(path.basename(jarPath))}`);

                const uploadRes = await fetch(uploadUrl, {
                    method: 'POST',
                    headers: {
                        'Authorization': `Bearer ${githubToken}`,
                        'Content-Type': 'application/java-archive',
                        'User-Agent': 'Node-Publisher'
                    },
                    body: jarBuffer
                });

                if (uploadRes.ok) {
                    console.log(`[SUCCESS] GitHub Release created: ${releaseData.html_url}`);
                } else {
                    console.error(`[ERROR] GitHub asset upload failed (${uploadRes.status}): ${await uploadRes.text()}`);
                }
            }
        } catch (err) {
            console.error('[ERROR] GitHub Release process failed:', err.message);
        }
    }

    // Step 4: Modrinth
    console.log('\n[STEP 4/4] Publishing to Modrinth & Hangar...');
    const modrinthToken = process.env.MODRINTH_TOKEN;
    const modrinthProjectId = process.env.MODRINTH_PROJECT_ID;

    if (!modrinthToken || !modrinthProjectId) {
        console.log('[SKIP] MODRINTH_TOKEN or MODRINTH_PROJECT_ID missing in environment or .env.');
    } else {
        try {
            const formData = new FormData();
            const metadata = {
                name: `v${version}`,
                version_number: version,
                changelog: changelog,
                dependencies: [],
                game_versions: ["1.21", "1.21.1", "1.21.2", "1.21.3", "1.21.4"],
                version_type: isBeta ? "beta" : "release",
                loaders: ["paper", "spigot", "purpur"],
                featured: true,
                project_id: modrinthProjectId,
                file_parts: ["file"]
            };

            formData.append('data', JSON.stringify(metadata));
            const fileBlob = new Blob([jarBuffer], { type: 'application/java-archive' });
            formData.append('file', fileBlob, path.basename(jarPath));

            const mrRes = await fetch('https://api.modrinth.com/v2/version', {
                method: 'POST',
                headers: {
                    'Authorization': modrinthToken,
                    'User-Agent': `${githubOwner}/${artifactId}/${version}`
                },
                body: formData
            });

            if (mrRes.ok) {
                const mrData = await mrRes.json();
                console.log(`[SUCCESS] Modrinth release published successfully (ID: ${mrData.id})`);
            } else {
                console.error(`[ERROR] Modrinth publishing failed (${mrRes.status}): ${await mrRes.text()}`);
            }
        } catch (err) {
            console.error('[ERROR] Modrinth release failed:', err.message);
        }
    }

    // Step 5: Hangar
    const hangarApiKey = process.env.HANGAR_API_KEY;
    const hangarAuthor = process.env.HANGAR_AUTHOR || githubOwner;
    const hangarSlug = process.env.HANGAR_SLUG || artifactId.toLowerCase();

    if (!hangarApiKey) {
        console.log('[SKIP] HANGAR_API_KEY missing in environment or .env.');
    } else {
        try {
            const authRes = await fetch(`https://hangar.papermc.io/api/v1/authenticate?apiKey=${encodeURIComponent(hangarApiKey)}`, {
                method: 'POST',
                headers: { 'User-Agent': `${githubOwner}/${artifactId}/${version}` }
            });

            if (!authRes.ok) {
                console.error(`[ERROR] Hangar authentication failed (${authRes.status}): ${await authRes.text()}`);
            } else {
                const authData = await authRes.json();
                const hangarJwt = authData.token;

                const hangarForm = new FormData();
                const versionUpload = {
                    version: version,
                    description: changelog,
                    platformDependencies: {
                        PAPER: ["1.21.x"]
                    },
                    files: [
                        {
                            plugin: true,
                            platforms: ["PAPER"]
                        }
                    ]
                };

                hangarForm.append('version_upload', JSON.stringify(versionUpload));
                const fileBlob = new Blob([jarBuffer], { type: 'application/java-archive' });
                hangarForm.append('files', fileBlob, path.basename(jarPath));

                const uploadRes = await fetch(`https://hangar.papermc.io/api/v1/projects/${hangarAuthor}/${hangarSlug}/upload`, {
                    method: 'POST',
                    headers: {
                        'Authorization': `Bearer ${hangarJwt}`,
                        'User-Agent': `${githubOwner}/${artifactId}/${version}`
                    },
                    body: hangarForm
                });

                if (uploadRes.ok) {
                    console.log(`[SUCCESS] Hangar release published successfully.`);
                } else {
                    console.error(`[ERROR] Hangar upload failed (${uploadRes.status}): ${await uploadRes.text()}`);
                }
            }
        } catch (err) {
            console.error('[ERROR] Hangar release failed:', err.message);
        }
    }

    console.log(`\n==============================================`);
    console.log(` Publishing process complete!`);
    console.log(`==============================================`);
}

main().catch(err => {
    console.error('Fatal error:', err);
    process.exit(1);
});

/*
 * Fails the build if the site would fetch anything from a host that is not its own.
 *
 * The site is meant to be self-contained: a visitor reading the documentation
 * should not, as a side effect, be announced to Google, to an analytics vendor
 * or to a slideshow host. The Jekyll site did all three, and two of them had
 * been quietly broken for years, which is exactly why this is checked by a
 * machine rather than by remembering.
 *
 * Only attributes that actually cause a request are inspected. An <a href> to
 * github.com is navigation the reader chooses to follow; a <script src> is a
 * request their browser makes on their behalf, without asking. The first is
 * fine and the site is full of them; the second is what this forbids.
 *
 * Usage: node scripts/check-external-references.mjs <build-dir> [own-host]
 */
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';

const root = process.argv[2] ?? 'dist';
const ownHost = process.argv[3] ?? 'matteobaccan.github.io';

function* walk(dir) {
  for (const entry of readdirSync(dir)) {
    const path = join(dir, entry);
    if (statSync(path).isDirectory()) yield* walk(path);
    else if (/\.(html|css|js)$/.test(entry)) yield path;
  }
}

const PATTERNS = [
  [/<script[^>]+src=["']([^"']+)["']/gi, 'script src'],
  [/<img[^>]+src=["']([^"']+)["']/gi, 'img src'],
  [/<img[^>]+srcset=["']([^"']+)["']/gi, 'img srcset'],
  [/<iframe[^>]+src=["']([^"']+)["']/gi, 'iframe src'],
  [/<video[^>]+src=["']([^"']+)["']/gi, 'video src'],
  [/<audio[^>]+src=["']([^"']+)["']/gi, 'audio src'],
  [/<source[^>]+src(?:set)?=["']([^"']+)["']/gi, 'source'],
  [
    /<link[^>]+rel=["'](?:stylesheet|preload|prefetch|preconnect|dns-prefetch|icon|manifest|modulepreload)["'][^>]*href=["']([^"']+)["']/gi,
    'link rel',
  ],
  [
    /<link[^>]+href=["']([^"']+)["'][^>]*rel=["'](?:stylesheet|preload|prefetch|preconnect|dns-prefetch|icon|manifest|modulepreload)["']/gi,
    'link rel',
  ],
  [/@import\s+(?:url\()?["']([^"')]+)["']/gi, 'css @import'],
  [/url\(\s*["']?((?:https?:)?\/\/[^"')]+)["']?\s*\)/gi, 'css url()'],
];

const offenders = new Set();

for (const file of walk(root)) {
  const text = readFileSync(file, 'utf8');
  for (const [re, kind] of PATTERNS) {
    re.lastIndex = 0;
    let match;
    while ((match = re.exec(text)) !== null) {
      const url = match[1].trim();
      if (!/^(https?:)?\/\//i.test(url)) continue; // relative: we serve it ourselves
      const host = url.replace(/^(https?:)?\/\//i, '').split(/[/?#]/)[0];
      if (host === ownHost) continue;
      offenders.add(`${file.slice(root.length)} [${kind}] ${url}`);
    }
  }
}

if (offenders.size > 0) {
  console.error(`FAIL — ${offenders.size} external runtime reference(s) found:`);
  for (const offender of offenders) console.error('  ' + offender);
  console.error(
    '\nEvery asset the site loads must be served from the site itself.\n' +
      'Vendor the file into the build instead of linking to it.'
  );
  process.exit(1);
}

console.log(`PASS — no resource is loaded from any host other than ${ownHost}.`);

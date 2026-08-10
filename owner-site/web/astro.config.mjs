// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';

// The site is published to the gh-pages branch of matteobaccan/owner and served
// from https://matteobaccan.github.io/owner/. `base` keeps every generated URL
// under /owner/, which is what makes the historical documentation links
// (/owner/docs/welcome/ and friends) survive the move away from Jekyll.
//
// English lives at the root of the locale tree rather than under /en/, for the
// same reason: /owner/docs/welcome/ must stay exactly where it is. Italian and
// Chinese get a prefix, and any page they have not translated yet falls back to
// the English one instead of 404ing.
export default defineConfig({
  site: 'https://matteobaccan.github.io',
  base: '/owner',
  trailingSlash: 'always',
  // /docs/xml-support/ and /docs/dotenv-support/ were never real pages: under
  // Jekyll they were HTML stubs whose only job was a meta refresh onto the
  // relevant section of "File formats". They keep that job here, as real
  // redirects rather than a refresh the browser has to execute.
  // /news/releases/ was a working address on the Jekyll site — the page that
  // listed every release announcement one after the other. That page is now
  // /news/ itself, so the old address points at it rather than dying.
  redirects: {
    '/docs/': '/docs/welcome/',
    '/docs/xml-support/': '/docs/file-formats/#xml',
    '/docs/dotenv-support/': '/docs/file-formats/#env',
    '/news/releases/': '/news/',
  },
  integrations: [
    starlight({
      title: 'OWNER',
      tagline: 'Java™ properties reinvented.',
      description:
        'Get rid of the boilerplate code in properties based configuration.',
      favicon: '/favicon.png',
      defaultLocale: 'root',
      locales: {
        root: { label: 'English', lang: 'en' },
        it: { label: 'Italiano', lang: 'it' },
        'zh-cn': { label: '简体中文', lang: 'zh-CN' },
      },
      social: [
        {
          icon: 'github',
          label: 'GitHub',
          href: 'https://github.com/matteobaccan/owner',
        },
      ],
      editLink: {
        baseUrl: 'https://github.com/matteobaccan/owner/edit/master/owner-site/web/',
      },
      lastUpdated: true,
      components: {
        // Adds the "not translated yet" notice on fallback pages.
        Banner: './src/components/Banner.astro',
        // Restores the maintainer, JetBrains and hosting credits.
        Footer: './src/components/Footer.astro',
      },
      // Lato is self-hosted through the @fontsource package: no request ever
      // leaves the visitor's browser for a font. CJK text falls back to the
      // system stack declared in owner.css, since shipping a Chinese webfont
      // would mean megabytes of payload.
      customCss: [
        '@fontsource/lato/400.css',
        '@fontsource/lato/700.css',
        '@fontsource/lato/900.css',
        './src/styles/owner.css',
      ],
      sidebar: [
        {
          label: 'Getting Started',
          translations: { it: 'Per iniziare', 'zh-CN': '开始使用' },
          items: [
            { label: 'Welcome', slug: 'docs/welcome' },
            { label: 'Installation', slug: 'docs/installation' },
            { label: 'Basic usage', slug: 'docs/usage' },
          ],
        },
        {
          label: 'More Features',
          translations: { it: 'Altre funzionalità', 'zh-CN': '更多功能' },
          items: [
            { label: 'Key prefix', slug: 'docs/key-prefix' },
            { label: 'Loading strategies', slug: 'docs/loading-strategies' },
            { label: 'Importing properties', slug: 'docs/importing-properties' },
            { label: 'Parametrized properties', slug: 'docs/parametrized-properties' },
            { label: 'Type conversion', slug: 'docs/type-conversion' },
            { label: 'Variables expansion', slug: 'docs/variables-expansion' },
            { label: 'Reload and Hot Reload', slug: 'docs/reload' },
            { label: 'Accessible and Mutable', slug: 'docs/accessible-mutable' },
            { label: 'Debugging and sensitive values', slug: 'docs/debugging' },
            { label: 'Disabling features', slug: 'docs/disabling-features' },
            { label: 'Metaconfiguring', slug: 'docs/configuring' },
            { label: 'File formats', slug: 'docs/file-formats' },
            { label: 'Event support', slug: 'docs/event-support' },
            { label: 'Singleton', slug: 'docs/singleton' },
            { label: 'Crypto support', slug: 'docs/crypto' },
            { label: 'Preprocessors', slug: 'docs/preprocessors' },
            { label: 'JMX support', slug: 'docs/jmx' },
          ],
        },
        {
          label: 'Miscellaneous',
          translations: { it: 'Varie', 'zh-CN': '其他' },
          items: [
            { label: 'Features', slug: 'docs/features' },
            { label: 'Why OWNER?', slug: 'docs/why' },
            { label: 'FAQ', slug: 'docs/faq' },
            { label: 'Getting support', slug: 'docs/support' },
          ],
        },
        {
          label: 'Meta',
          translations: { it: 'Progetto', 'zh-CN': '项目' },
          items: [
            { label: 'Building from sources', slug: 'docs/building' },
            { label: 'Contributing', slug: 'docs/contributing' },
            { label: 'License', slug: 'docs/license' },
          ],
        },
        {
          label: 'Other stuff',
          translations: { it: 'Altro', 'zh-CN': '其他资源' },
          items: [
            { label: "What's new", slug: 'news' },
            // The Javadoc is not part of this build: it is published onto the
            // gh-pages branch by `ant javadoc publish` and merely lives next to
            // us. Spelled as a full URL on purpose — given as a root-relative
            // path, `trailingSlash: 'always'` rewrites it to
            // /apidocs/latest/index/ and the link dies.
            {
              label: 'Javadoc',
              link: 'https://matteobaccan.github.io/owner/apidocs/latest/index.html',
            },
            { label: 'Downloads', link: 'https://central.sonatype.com/artifact/org.aeonbits.owner/owner' },
            { label: 'Releases', link: 'https://github.com/matteobaccan/owner/releases' },
            { label: 'Discussions', link: 'https://github.com/matteobaccan/owner/discussions' },
            { label: 'CI reports', link: 'https://github.com/matteobaccan/owner/actions' },
            { label: 'Sonar reports', link: 'https://sonarcloud.io/project/overview?id=matteobaccan_owner' },
          ],
        },
      ],
    }),
  ],
});

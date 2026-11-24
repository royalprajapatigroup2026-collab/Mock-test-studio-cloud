MOCK TEST STUDIO — CLOUD PUBLISHER TEST PACKAGE
================================================

This package contains the original Mock Test Studio PWA plus an approval-based Gemini JSON Publisher and Netlify Functions backend.

IMPORTANT: This updated package is NOT a static drag-and-drop-only deployment. It includes Netlify Functions and uses Netlify Blobs. Deploy it through a NEW Git-connected Netlify test site (or Netlify CLI), not by dropping the ZIP into Netlify Drop. See SETUP-ANDROID-HINDI.txt for the complete Android-friendly steps.

The original live site has not been changed. Test on a separate Netlify site first.

Added files:
- publisher.html
- netlify/functions/api-questions.mjs
- netlify.toml
- package.json
- SETUP-ANDROID-HINDI.txt

The PWA adds:
- Sync Published Questions
- Open Question Publisher

Data safety:
- Cloud sync appends approved batches to the existing Question Bank storage key.
- It does not clear or replace existing questions, results, bookmarks, or settings.
- Batch IDs prevent repeated import of the same published batch on each device.
- Local validation blocks same-question/different-answer conflicts before saving.
- Always create a Full Backup before testing.

The publisher requires a Netlify Environment Variable named PUBLISHER_TOKEN. Never place this secret in source code or the PWA. Follow SETUP-ANDROID-HINDI.txt.

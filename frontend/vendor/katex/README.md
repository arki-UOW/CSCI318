# Local equation rendering

These browser assets are copied from the `katex@0.18.4` npm package (`dist/katex.min.js`, `dist/katex.min.css`, `dist/contrib/auto-render.min.js`, and `dist/fonts/`). The upstream MIT licence is included as `LICENSE`.

The package and lockfile pin the same version for reproducibility. When updating, copy these files from the installed package together, preserve the licence, and run the full-document frontend test. Keeping the assets local lets the Docker frontend render equations without a CDN connection. Provider answers remain text nodes; KaTeX runs with untrusted-command support disabled.

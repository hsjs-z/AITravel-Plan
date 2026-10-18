/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_AMAP_JS_KEY?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

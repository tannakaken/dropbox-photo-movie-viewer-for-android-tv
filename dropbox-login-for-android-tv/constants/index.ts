export const DEFAULT_BASE_URL = 'http://localhost:3000';
export const DEFAULT_CALLBACK_URL = 'http://localhost:3000/api/auth/callback';
export const DROPBOX_TOKEN_URL = 'https://api.dropbox.com/oauth2/token';
/**
 * httpの独自ヘッダのキーをX-Somethingのような名前にすることは現在は推奨されていない。
 * 
 * @see https://zenn.dev/ys/articles/a58b02e3cbc2f839f7f1
 * 
 * これは
 * - X-Somethingという名前のまま普及してしまうケースも多い。
 * - そのヘッダーが正式の仕様に採用されたときに、X-を外すのが難しいので、両方対応することになりがち。
 * という理由。
 * 
 * 結局は「絶対に重ならない名前にする」という解決方法しかないことになる。
 * 念のためX-もつけておく。
 */
export const DEVICE_GENERATE_ID_HEADER_KEY = 'X-Tannakaken-Android-TV-Dropbox-Device-Generate-ID';
export const AUTHORIZATION_HEADER_KEY = 'Authorization';

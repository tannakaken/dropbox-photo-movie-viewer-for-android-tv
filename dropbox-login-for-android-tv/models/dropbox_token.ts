
/**
 * Dropboxからトークンを取得するレスポンス
 */
export type TokenResponseFromDropbox = {
  access_token: string;
  refresh_token: string;
  token_type: 'bearer';
  /**
   * Dropboxのaccess_tokenの寿命は14400秒=4時間と短い
   */
  expires_in: number;
  scope: string;
  uid: string;
  account_id: string;
};

/**
 * DropboxのアクセストークンをAndroid TVに返すレスポンス
 */
export type DropboxTokenResponse = {
  dropboxAccessToken: string;
};

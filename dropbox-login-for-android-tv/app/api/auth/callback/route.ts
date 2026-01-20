import { DEFAULT_BASE_URL, DEFAULT_CALLBACK_URL, DROPBOX_TOKEN_URL } from '@/constants';
import { DropboxTokenResponse } from '@/models/dropbox_token';
import { getFlowData, setFlowData } from '@/utils/api/redis';
import { NextRequest, NextResponse } from 'next/server';

export async function GET(req: NextRequest) {
  const { searchParams } = new URL(req.url);
  const code = searchParams.get('code');
  const state = searchParams.get('state');
  if (state === null) {
    console.error('Missing state');
    return NextResponse.json({ error: 'Missing state' }, { status: 400 });
  }
  const flowData = await getFlowData(state);
  if (flowData === null) {
    console.error('Invalid state');
    return NextResponse.json({ error: 'Invalid state' }, { status: 400 });
  }

  if (code === null) {
    console.error('Missing code');
    return NextResponse.json({ error: 'Missing code' }, { status: 400 });
  }

  const { NEXT_PUBLIC_DROPBOX_APP_KEY, DROPBOX_APP_SECRET } = process.env;
  const NEXT_PUBLIC_DROPBOX_REDIRECT_URI = process.env.NEXT_PUBLIC_DROPBOX_REDIRECT_URI || DEFAULT_CALLBACK_URL;


  if (!NEXT_PUBLIC_DROPBOX_APP_KEY || !DROPBOX_APP_SECRET) {
    console.error('Missing Dropbox environment variables');
    return NextResponse.json({ error: 'Server configuration error' }, { status: 500 });
  }

  const params = new URLSearchParams();
  params.append('code', code);
  params.append('grant_type', 'authorization_code');
  params.append('redirect_uri', NEXT_PUBLIC_DROPBOX_REDIRECT_URI);
  params.append('client_id', NEXT_PUBLIC_DROPBOX_APP_KEY);
  params.append('client_secret', DROPBOX_APP_SECRET);

  try {
    const response = await fetch(DROPBOX_TOKEN_URL, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
      },
      body: params,
    });

    const data = await response.json();

    if (!response.ok) {
      console.error('Dropbox token exchange error:', data);
      return NextResponse.json({ error: 'Failed to fetch token', details: data }, { status: response.status });
    }

    const dropboxTokenResponse = data as DropboxTokenResponse;

    await setFlowData(state, {
      ...flowData,
      completed: true,
      dropboxRefreshToken: dropboxTokenResponse.refresh_token,
    });
    const BASE_URL = process.env.NEXT_AUTH_URL || DEFAULT_BASE_URL;
    return NextResponse.redirect(new URL('/success', BASE_URL));
  } catch (error) {
    console.error('Error during token exchange:', error);
    return NextResponse.json({ error: 'Internal Server Error' }, { status: 500 });
  }
}

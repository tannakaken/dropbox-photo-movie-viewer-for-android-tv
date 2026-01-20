"use client";

import { DEFAULT_CALLBACK_URL } from "@/constants";
import Image from "next/image";
import { useSearchParams } from "next/navigation";

export default function Home() {
  const searchParams = useSearchParams();
  const state = searchParams.get('state');

  const handleLogin = () => {
    if (state === null) {
      return;
    }

    const dropboxAppKey = process.env.NEXT_PUBLIC_DROPBOX_APP_KEY;
    if (!dropboxAppKey) {
        console.error("NEXT_PUBLIC_DROPBOX_APP_KEY environment variable is not set on the client.");
        alert("Dropbox integration is not configured. Please contact the site administrator.");
        return;
    }
    
    const redirectUri = process.env.NEXT_PUBLIC_DROPBOX_REDIRECT_URI || DEFAULT_CALLBACK_URL;
    /**
     * refresh_tokenを取得するためにtoken_access_type=offlineが必要
     */
    const authUrl = `https://www.dropbox.com/oauth2/authorize?client_id=${dropboxAppKey}&response_type=code&redirect_uri=${encodeURIComponent(redirectUri)}&token_access_type=offline&state=${state}`;
    
    // ページの変更
    window.location.href = authUrl;
  };

  if (state === null) {
    <h1>Error</h1>
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-zinc-50 font-sans dark:bg-black">
      <main className="flex min-h-screen w-full max-w-3xl flex-col items-center justify-between py-32 px-16 bg-white dark:bg-black sm:items-start">
        <div className="flex flex-col items-center gap-6 text-center sm:items-start sm:text-left">
          <h1 className="text-3xl font-semibold leading-10 tracking-tight text-black dark:text-zinc-50">
            Dropbox Photo & Movie Viewerにアカウントをリンクさせます。
          </h1>
        </div>
        <div className="flex flex-col items-center gap-6 text-center sm:items-start sm:text-left">
          <Image
              className="dark:invert"
              src="/Dropbox_logo_2017.svg"
              alt="Dropbox logomark"
              width={200}
              height={40}
            />
        </div>
        <div className="flex flex-col gap-4 text-base font-medium sm:flex-row">
          <button
            onClick={handleLogin}
            className="flex h-12 w-full items-center justify-center gap-2 rounded-full bg-blue-600 px-5 text-white transition-colors hover:bg-blue-700 md:w-auto"
          >
            Login with Dropbox
          </button>
        </div>
      </main>
    </div>
  );
}

'use client';

export default function SuccessPage() {
    return (
        <div className="flex items-center justify-center min-h-screen bg-linear-to-br from-blue-50 to-indigo-100">
            <div className="text-center">
                <div className="mb-6">
                    <svg
                        className="w-16 h-16 mx-auto text-green-500"
                        fill="none"
                        stroke="currentColor"
                        viewBox="0 0 24 24"
                    >
                        <path
                            strokeLinecap="round"
                            strokeLinejoin="round"
                            strokeWidth={2}
                            d="M5 13l4 4L19 7"
                        />
                    </svg>
                </div>
                <h1 className="text-4xl font-bold text-gray-800 mb-2">Success!</h1>
                <p className="text-lg text-gray-600 mb-6">
                    Your Dropbox login was successful
                </p>
                <p className="text-sm text-gray-500">
                    Please close this page.
                </p>
            </div>
        </div>
    );
}
export type FlowData = {
    token: string;
    deviceGenerateId: string;
} & ({
    completed: false;
    dropboxRefreshToken?: undefined
} | {
    completed: true;
    dropboxRefreshToken: string;
});

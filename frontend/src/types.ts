
export interface User {
    id: string;
    username: string;
    full_name: string;
    email: string;
}

export interface Video {
    id: string;
    filename: string;
    author: User;
    video_name: string;
    video_desc: string;
}

export interface Token {
    access_token: string;
    token_type: string;
}
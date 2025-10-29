# YunBQ Backend API

## Notes API

### GET `/api/notes`
- Query params:
  - `page` (default: `1`)
  - `size` (default: `10`)
  - `q` (optional, search in title/content/tags)
  - `archived` (optional, `true` or `false`)
  - `isPublic` (optional, `true` or `false`)
- Response:
```
{
  "items": [
    {
      "id": 1,
      "userId": 100,
      "title": "...",
      "content": "...",
      "tags": "tag1,tag2",
      "archived": false,
      "isPublic": true,
      "createdAt": "2025-10-26T12:34:56",
      "updatedAt": "2025-10-26T12:35:00",
      "likeCount": 3,
      "likedByMe": true
    }
  ],
  "total": 42,
  "page": 1,
  "size": 10
}
```

### POST `/api/notes/{id}/like`
- Returns `{ "count": number, "likedByMe": true }`

### POST `/api/notes/{id}/unlike`
- Returns `{ "count": number, "likedByMe": false }`

### GET `/api/notes/{id}/likes`
- Returns `{ "count": number, "likedByMe": boolean }`

## Notes Model
`NoteItem` fields in list responses:
- `id`, `userId`, `authorName`, `title`, `content`, `tags`
- `archived`, `isPublic`, `createdAt`, `updatedAt`
- `likeCount`, `likedByMe`

## Auth
- All endpoints require valid `Authorization: Bearer <token>` header.
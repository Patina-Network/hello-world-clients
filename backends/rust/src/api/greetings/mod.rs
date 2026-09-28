pub mod body;

use axum::{
    Json,
    extract::{Query, State, rejection::JsonRejection},
    http::StatusCode,
};
use serde::Deserialize;
use serde_json::{Value, json};

use crate::{
    api::AppState,
    common::{bounded, request, valid},
    pb::{GetGreetingsByNameRequest, SayGreetingRequest},
    utilities::exception::ApiError,
};

use body::SayGreetingBody;

pub async fn send(
    State(mut state): State<AppState>,
    request_body: Result<Json<SayGreetingBody>, JsonRejection>,
) -> Result<Json<Value>, ApiError> {
    let Json(input) = request_body.map_err(|rejection| {
        ApiError(
            if rejection.status() == StatusCode::UNPROCESSABLE_ENTITY {
                StatusCode::BAD_REQUEST
            } else {
                rejection.status()
            },
            "invalid JSON request",
        )
    })?;
    if !valid(&input.sender_name, 256)
        || !valid(&input.recipient_name, 256)
        || !valid(&input.greeting, 4096)
    {
        return Err(ApiError(
            StatusCode::BAD_REQUEST,
            "senderName, recipientName and greeting are required (256/256/4096 byte limits)",
        ));
    }
    bounded(
        state.timeout,
        state.client.say_greeting(request(
            SayGreetingRequest {
                sender_name: input.sender_name,
                recipient_name: input.recipient_name,
                greeting: input.greeting,
            },
            state.timeout,
        )),
    )
    .await?;
    Ok(Json(json!({})))
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ListQuery {
    recipient_name: Option<String>,
}

pub async fn list(
    State(mut state): State<AppState>,
    Query(query): Query<ListQuery>,
) -> Result<Json<Value>, ApiError> {
    if query
        .recipient_name
        .as_ref()
        .is_some_and(|name| name.len() > 256)
    {
        return Err(ApiError(
            StatusCode::BAD_REQUEST,
            "recipientName exceeds 256 UTF-8 bytes",
        ));
    }
    let out = bounded(
        state.timeout,
        state.client.get_greetings_by_name(request(
            GetGreetingsByNameRequest {
                recipient_name: query.recipient_name,
            },
            state.timeout,
        )),
    )
    .await?;
    let replies: Vec<Value> = out
        .replies
        .into_iter()
        .map(|reply| {
            json!({
                "id": reply.id,
                "message": reply.message,
                "senderName": reply.sender_name,
                "recipientName": reply.recipient_name,
                "receivedAt": reply.received_at.map(|timestamp| timestamp.to_string()),
            })
        })
        .collect();
    Ok(Json(json!({"replies": replies})))
}

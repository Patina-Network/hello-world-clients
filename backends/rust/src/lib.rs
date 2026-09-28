use axum::{
    Json, Router,
    extract::{DefaultBodyLimit, Query, State, rejection::JsonRejection},
    http::StatusCode,
    response::{IntoResponse, Response},
    routing::get,
};
use serde::Deserialize;
use serde_json::{Value, json};
use std::time::Duration;
use tonic::{Code, Request, transport::Channel};
use tower_http::services::ServeDir;

pub mod pb {
    tonic::include_proto!("helloworld");
}
use pb::{
    EchoHelloRequest, GetGreetingsByNameRequest, SayGreetingRequest,
    greeter_service_client::GreeterServiceClient,
};

#[derive(Clone)]
pub struct AppState {
    pub client: GreeterServiceClient<Channel>,
    pub timeout: Duration,
}

pub fn app(state: AppState, static_dir: &str) -> Router {
    Router::new()
        .route("/healthz", get(|| async { Json(json!({"status":"ok"})) }))
        .route("/api/echo", get(echo))
        .route("/api/greetings", get(list).post(send))
        .route(
            "/api/{*path}",
            get(|| async { ApiError(StatusCode::NOT_FOUND, "not found") }),
        )
        .fallback_service(ServeDir::new(static_dir))
        .layer(DefaultBodyLimit::max(16 * 1024))
        .with_state(state)
}
#[derive(Debug)]
pub struct ApiError(StatusCode, &'static str);
impl IntoResponse for ApiError {
    fn into_response(self) -> Response {
        (self.0, Json(json!({"error":self.1}))).into_response()
    }
}
impl From<tonic::Status> for ApiError {
    fn from(s: tonic::Status) -> Self {
        tracing::warn!(code=?s.code(),"gRPC request failed");
        match s.code() {
            Code::InvalidArgument => Self(StatusCode::BAD_REQUEST, "invalid request"),
            Code::NotFound => Self(StatusCode::NOT_FOUND, "not found"),
            Code::AlreadyExists => Self(StatusCode::CONFLICT, "already exists"),
            Code::Unauthenticated => Self(StatusCode::UNAUTHORIZED, "authentication required"),
            Code::PermissionDenied => Self(StatusCode::FORBIDDEN, "permission denied"),
            Code::ResourceExhausted => Self(StatusCode::TOO_MANY_REQUESTS, "resource exhausted"),
            Code::Unavailable => Self(StatusCode::SERVICE_UNAVAILABLE, "service unavailable"),
            Code::DeadlineExceeded | Code::Cancelled => {
                Self(StatusCode::GATEWAY_TIMEOUT, "upstream timeout")
            }
            _ => Self(StatusCode::BAD_GATEWAY, "upstream request failed"),
        }
    }
}
fn valid(s: &str, max: usize) -> bool {
    !s.trim().is_empty() && s.len() <= max
}
fn request<T>(value: T, timeout: Duration) -> Request<T> {
    let mut req = Request::new(value);
    req.set_timeout(timeout);
    req
}
async fn bounded<T>(
    timeout: Duration,
    future: impl std::future::Future<Output = Result<tonic::Response<T>, tonic::Status>>,
) -> Result<T, ApiError> {
    tokio::time::timeout(timeout, future)
        .await
        .map_err(|_| ApiError(StatusCode::GATEWAY_TIMEOUT, "upstream timeout"))?
        .map(tonic::Response::into_inner)
        .map_err(Into::into)
}
#[derive(Deserialize)]
struct EchoQuery {
    #[serde(default)]
    name: String,
}
async fn echo(
    State(mut s): State<AppState>,
    Query(q): Query<EchoQuery>,
) -> Result<Json<Value>, ApiError> {
    if !valid(&q.name, 256) {
        return Err(ApiError(
            StatusCode::BAD_REQUEST,
            "name must contain 1–256 UTF-8 bytes",
        ));
    }
    let out = bounded(
        s.timeout,
        s.client
            .echo_hello(request(EchoHelloRequest { name: q.name }, s.timeout)),
    )
    .await?;
    Ok(Json(json!({"response":out.response})))
}
#[derive(Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
struct GreetingInput {
    sender_name: String,
    recipient_name: String,
    greeting: String,
}
async fn send(
    State(mut s): State<AppState>,
    body: Result<Json<GreetingInput>, JsonRejection>,
) -> Result<Json<Value>, ApiError> {
    let Json(input) = body.map_err(|e| {
        ApiError(
            if e.status() == StatusCode::UNPROCESSABLE_ENTITY {
                StatusCode::BAD_REQUEST
            } else {
                e.status()
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
        s.timeout,
        s.client.say_greeting(request(
            SayGreetingRequest {
                sender_name: input.sender_name,
                recipient_name: input.recipient_name,
                greeting: input.greeting,
            },
            s.timeout,
        )),
    )
    .await?;
    Ok(Json(json!({})))
}
#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct ListQuery {
    recipient_name: Option<String>,
}
async fn list(
    State(mut s): State<AppState>,
    Query(q): Query<ListQuery>,
) -> Result<Json<Value>, ApiError> {
    if q.recipient_name.as_ref().is_some_and(|n| n.len() > 256) {
        return Err(ApiError(
            StatusCode::BAD_REQUEST,
            "recipientName exceeds 256 UTF-8 bytes",
        ));
    }
    let out = bounded(
        s.timeout,
        s.client.get_greetings_by_name(request(
            GetGreetingsByNameRequest {
                recipient_name: q.recipient_name,
            },
            s.timeout,
        )),
    )
    .await?;
    let replies:Vec<Value>=out.replies.into_iter().map(|r|json!({"id":r.id,"message":r.message,"senderName":r.sender_name,"recipientName":r.recipient_name,"receivedAt":r.received_at.map(|t|t.to_string())})).collect();
    Ok(Json(json!({"replies":replies})))
}

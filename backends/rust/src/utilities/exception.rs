use axum::{
    Json,
    http::StatusCode,
    response::{IntoResponse, Response},
};
use serde_json::json;
use tonic::Code;

#[derive(Debug)]
pub struct ApiError(pub StatusCode, pub &'static str);

impl IntoResponse for ApiError {
    fn into_response(self) -> Response {
        (self.0, Json(json!({"error": self.1}))).into_response()
    }
}

impl From<tonic::Status> for ApiError {
    fn from(status: tonic::Status) -> Self {
        tracing::warn!(code = ?status.code(), "gRPC request failed");
        match status.code() {
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

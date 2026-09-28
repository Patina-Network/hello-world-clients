use std::{future::Future, time::Duration};

use axum::http::StatusCode;
use tonic::Request;

use crate::utilities::exception::ApiError;

pub fn valid(value: &str, max_bytes: usize) -> bool {
    !value.trim().is_empty() && value.len() <= max_bytes
}

pub fn request<T>(message: T, timeout: Duration) -> Request<T> {
    let mut request = Request::new(message);
    request.set_timeout(timeout);
    request
}

pub async fn bounded<T>(
    timeout: Duration,
    call: impl Future<Output = Result<tonic::Response<T>, tonic::Status>>,
) -> Result<T, ApiError> {
    tokio::time::timeout(timeout, call)
        .await
        .map_err(|_| ApiError(StatusCode::GATEWAY_TIMEOUT, "upstream timeout"))?
        .map(tonic::Response::into_inner)
        .map_err(Into::into)
}

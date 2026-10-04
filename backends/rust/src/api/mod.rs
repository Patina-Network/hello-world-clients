mod echo;
mod greetings;
mod health;

use std::time::Duration;

use axum::{Router, extract::DefaultBodyLimit, http::StatusCode, routing::get};
use tonic::transport::Channel;

use crate::{
    pb::greeter_service_client::GreeterServiceClient,
    utilities::{exception::ApiError, static_content::static_files},
};

#[derive(Clone)]
pub struct AppState {
    pub client: GreeterServiceClient<Channel>,
    pub timeout: Duration,
}

pub fn app(state: AppState, static_dir: &str) -> Router {
    Router::new()
        .route("/healthz", get(health::health))
        .route("/api/echo", get(echo::echo))
        .route("/api/greetings", get(greetings::list).post(greetings::send))
        .route(
            "/api/{*path}",
            get(|| async { ApiError(StatusCode::NOT_FOUND, "not found") }),
        )
        .fallback_service(static_files(static_dir))
        .layer(DefaultBodyLimit::max(16 * 1024))
        .with_state(state)
}

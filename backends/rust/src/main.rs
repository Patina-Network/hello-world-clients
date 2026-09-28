use hello_world_client_rust::{AppState, app, pb::greeter_service_client::GreeterServiceClient};
use std::{env, time::Duration};
use tonic::transport::{ClientTlsConfig, Endpoint};
fn env_or(key: &str, fallback: &str) -> String {
    env::var(key).unwrap_or_else(|_| fallback.into())
}
#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    tracing_subscriber::fmt()
        .json()
        .with_env_filter(
            tracing_subscriber::EnvFilter::try_from_default_env().unwrap_or_else(|_| "info".into()),
        )
        .init();
    let timeout_ms: u64 = env_or("GRPC_TIMEOUT_MS", "3000").parse()?;
    if timeout_ms == 0 || timeout_ms > 30000 {
        return Err("GRPC_TIMEOUT_MS must be 1–30000".into());
    }
    let timeout = Duration::from_millis(timeout_ms);
    let tls = match env_or("GRPC_TLS", "false").as_str() {
        "true" => true,
        "false" => false,
        _ => return Err("GRPC_TLS must be true or false".into()),
    };
    let target = env_or("GRPC_TARGET", "hello-world-grpc-service:50051");
    let mut endpoint =
        Endpoint::from_shared(format!("{}://{target}", if tls { "https" } else { "http" }))?
            .connect_timeout(timeout)
            .timeout(timeout);
    if tls {
        endpoint = endpoint.tls_config(ClientTlsConfig::new().with_native_roots())?;
    }
    let state = AppState {
        client: GreeterServiceClient::new(endpoint.connect_lazy()),
        timeout,
    };
    let listener = tokio::net::TcpListener::bind(env_or("HTTP_ADDR", "0.0.0.0:8080")).await?;
    tracing::info!(address=%listener.local_addr()?,"HTTP server started");
    axum::serve(
        listener,
        app(state, &env_or("STATIC_DIR", "../../frontend/dist")),
    )
    .with_graceful_shutdown(shutdown())
    .await?;
    Ok(())
}
async fn shutdown() {
    let ctrl_c = async {
        tokio::signal::ctrl_c()
            .await
            .expect("install Ctrl-C handler")
    };
    #[cfg(unix)]
    let terminate = async {
        tokio::signal::unix::signal(tokio::signal::unix::SignalKind::terminate())
            .expect("install SIGTERM handler")
            .recv()
            .await;
    };
    #[cfg(not(unix))]
    let terminate = std::future::pending::<()>();
    tokio::select! {_=ctrl_c=>{},_=terminate=>{}}
}

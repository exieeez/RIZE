use std::env;
use std::net::TcpStream;
use std::path::PathBuf;
use std::process::Command;
use std::thread;
use std::time::Duration;

fn ensure_server() {
    let addr = "127.0.0.1:17217";
    if TcpStream::connect(addr).is_ok() {
        return;
    }

    let candidates = [
        PathBuf::from("server.js"),
        PathBuf::from("../server.js"),
        PathBuf::from("../../server.js"),
    ];

    let mut found_dir = None;
    for c in &candidates {
        if c.exists() {
            found_dir = c.parent().map(|p| p.to_path_buf());
            break;
        }
    }

    if found_dir.is_none() {
        if let Ok(exe) = env::current_exe() {
            if let Some(parent) = exe.parent() {
                for ancestor in parent.ancestors().take(6) {
                    let candidate = ancestor.join("server.js");
                    if candidate.exists() {
                        found_dir = Some(ancestor.to_path_buf());
                        break;
                    }
                }
            }
        }
    }

    let mut cmd = Command::new("node");
    cmd.arg("server.js").env("VOTIFY_PORT", "17217");
    if let Some(dir) = found_dir {
        cmd.current_dir(dir);
    }

    let _ = cmd.spawn();

    for _ in 0..60 {
        thread::sleep(Duration::from_millis(100));
        if TcpStream::connect(addr).is_ok() {
            break;
        }
    }
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    unsafe {
        std::env::set_var("GTK_THEME", "Adwaita:dark");
    }
    ensure_server();

    tauri::Builder::default()
        .setup(|app| {
            if cfg!(debug_assertions) {
                app.handle().plugin(
                    tauri_plugin_log::Builder::default()
                        .level(log::LevelFilter::Info)
                        .build(),
                )?;
            }
            Ok(())
        })
        .run(tauri::generate_context!())
        .expect("error while building tauri application");
}

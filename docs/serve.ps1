# Tiny static web server for testing the web game (no installs needed, Windows PowerShell 5+).
#   Usage (from the project root):   powershell -ExecutionPolicy Bypass -File docs\serve.ps1
#   Optional port:                   powershell -ExecutionPolicy Bypass -File docs\serve.ps1 -Port 9000
# Serves the folder this script is in (docs/) on all network interfaces, so a phone on the same Wi-Fi can open it.
# Supports HTTP Range requests (needed by iPhone Safari for <video>).
param([int]$Port = 8000)

$root = $PSScriptRoot

Add-Type -TypeDefinition @"
using System;
using System.Collections.Generic;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Threading;

public static class MiniServer
{
    static readonly Dictionary<string,string> Mime = new Dictionary<string,string>(StringComparer.OrdinalIgnoreCase) {
        {".html","text/html; charset=utf-8"}, {".htm","text/html; charset=utf-8"},
        {".js","text/javascript; charset=utf-8"}, {".mjs","text/javascript; charset=utf-8"},
        {".css","text/css; charset=utf-8"}, {".json","application/json; charset=utf-8"},
        {".png","image/png"}, {".jpg","image/jpeg"}, {".jpeg","image/jpeg"}, {".gif","image/gif"},
        {".webp","image/webp"}, {".svg","image/svg+xml"}, {".ico","image/x-icon"},
        {".mp4","video/mp4"}, {".webm","video/webm"},
        {".ogg","audio/ogg"}, {".mp3","audio/mpeg"}, {".m4a","audio/mp4"}, {".wav","audio/wav"},
        {".txt","text/plain; charset=utf-8"}, {".md","text/plain; charset=utf-8"},
        {".woff","font/woff"}, {".woff2","font/woff2"}, {".ttf","font/ttf"}
    };

    public static void Run(string root, int port)
    {
        TcpListener listener = new TcpListener(IPAddress.Any, port);
        listener.Start();
        while (true)
        {
            TcpClient client = listener.AcceptTcpClient();
            ThreadPool.QueueUserWorkItem(delegate { Handle(client, root); });
        }
    }

    static void Handle(TcpClient client, string root)
    {
        try
        {
            client.ReceiveTimeout = 15000;
            client.SendTimeout = 30000;
            using (client)
            using (NetworkStream stream = client.GetStream())
            {
                // read request head
                MemoryStream head = new MemoryStream();
                byte[] one = new byte[1];
                int state = 0;
                while (state < 4 && head.Length < 16384)
                {
                    if (stream.Read(one, 0, 1) <= 0) return;
                    head.WriteByte(one[0]);
                    if ((state % 2 == 0 && one[0] == 13) || (state % 2 == 1 && one[0] == 10)) state++; else state = (one[0] == 13) ? 1 : 0;
                }
                string[] lines = Encoding.ASCII.GetString(head.ToArray()).Split(new string[] { "\r\n" }, StringSplitOptions.None);
                string[] first = lines[0].Split(' ');
                if (first.Length < 2) return;
                string method = first[0];
                string url = Uri.UnescapeDataString(first[1].Split('?')[0]);
                string range = null;
                foreach (string l in lines)
                    if (l.StartsWith("Range:", StringComparison.OrdinalIgnoreCase)) range = l.Substring(6).Trim();

                if (method != "GET" && method != "HEAD") { Send(stream, 405, "Method Not Allowed", null, null); return; }

                string rel = url.TrimStart('/').Replace('/', Path.DirectorySeparatorChar);
                string full = Path.GetFullPath(Path.Combine(root, rel));
                string rootFull = Path.GetFullPath(root);
                if (!full.StartsWith(rootFull, StringComparison.OrdinalIgnoreCase)) { Send(stream, 403, "Forbidden", null, null); return; }
                if (Directory.Exists(full)) full = Path.Combine(full, "index.html");
                if (!File.Exists(full)) { Send(stream, 404, "Not Found", Encoding.UTF8.GetBytes("404 " + url), "text/plain"); Log(method, url, 404); return; }

                string ext = Path.GetExtension(full);
                string type; if (!Mime.TryGetValue(ext, out type)) type = "application/octet-stream";

                using (FileStream fs = new FileStream(full, FileMode.Open, FileAccess.Read, FileShare.Read))
                {
                    long len = fs.Length, start = 0, end = len - 1;
                    int status = 200; string statusText = "OK";
                    if (range != null && range.StartsWith("bytes="))
                    {
                        string[] parts = range.Substring(6).Split('-');
                        long s, e;
                        if (parts[0] == "") { long n; if (long.TryParse(parts[1], out n)) { start = Math.Max(0, len - n); } }
                        else if (long.TryParse(parts[0], out s)) { start = s; if (parts.Length > 1 && long.TryParse(parts[1], out e)) end = Math.Min(e, len - 1); }
                        if (start > end || start >= len) { Send(stream, 416, "Range Not Satisfiable", null, null); return; }
                        status = 206; statusText = "Partial Content";
                    }
                    long count = end - start + 1;
                    StringBuilder sb = new StringBuilder();
                    sb.Append("HTTP/1.1 " + status + " " + statusText + "\r\n");
                    sb.Append("Content-Type: " + type + "\r\n");
                    sb.Append("Content-Length: " + count + "\r\n");
                    sb.Append("Accept-Ranges: bytes\r\n");
                    if (status == 206) sb.Append("Content-Range: bytes " + start + "-" + end + "/" + len + "\r\n");
                    sb.Append("Cache-Control: no-cache\r\n");
                    sb.Append("Connection: close\r\n\r\n");
                    byte[] h = Encoding.ASCII.GetBytes(sb.ToString());
                    stream.Write(h, 0, h.Length);
                    if (method == "GET")
                    {
                        fs.Seek(start, SeekOrigin.Begin);
                        byte[] buf = new byte[65536];
                        long remaining = count;
                        while (remaining > 0)
                        {
                            int n = fs.Read(buf, 0, (int)Math.Min(buf.Length, remaining));
                            if (n <= 0) break;
                            stream.Write(buf, 0, n);
                            remaining -= n;
                        }
                    }
                    Log(method, url, status);
                }
            }
        }
        catch (Exception) { /* client closed the connection (normal for video seeking) */ }
    }

    static void Send(NetworkStream s, int code, string text, byte[] body, string type)
    {
        int len = body == null ? 0 : body.Length;
        string h = "HTTP/1.1 " + code + " " + text + "\r\nContent-Length: " + len + "\r\n" +
                   (type != null ? "Content-Type: " + type + "\r\n" : "") + "Connection: close\r\n\r\n";
        byte[] hb = Encoding.ASCII.GetBytes(h);
        s.Write(hb, 0, hb.Length);
        if (body != null) s.Write(body, 0, body.Length);
    }

    static void Log(string method, string url, int status)
    {
        Console.WriteLine("{0:HH:mm:ss} {1} {2} {3}", DateTime.Now, status, method, url);
    }
}
"@

Write-Host ""
Write-Host "Serving $root" -ForegroundColor Green
Write-Host "  On this PC : http://localhost:$Port/" -ForegroundColor Cyan
$ips = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
  Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*' -and $_.PrefixOrigin -ne 'WellKnown' }
foreach ($ip in $ips) {
  Write-Host ("  On iPhone  : http://{0}:{1}/   ({2})" -f $ip.IPAddress, $Port, $ip.InterfaceAlias) -ForegroundColor Cyan
}
Write-Host "Press Ctrl+C to stop." -ForegroundColor DarkGray
Write-Host ""

[MiniServer]::Run($root, $Port)

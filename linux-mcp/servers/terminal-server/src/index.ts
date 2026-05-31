import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/streamableHttp.js";
import express from "express";
import { spawn } from "child_process";
import { z } from "zod";

const PORT = parseInt(process.env.PORT || "18701", 10);
const DEFAULT_TIMEOUT_MS = 30_000;
const MAX_OUTPUT_BYTES = 1024 * 1024;

const server = new McpServer({
  name: "omnimaster-terminal",
  version: "1.0.0",
});

interface CommandResult {
  stdout: string;
  stderr: string;
  exitCode: number | null;
  timedOut: boolean;
}

function runCommand(
  command: string,
  cwd: string,
  env: Record<string, string>,
  timeoutMs: number
): Promise<CommandResult> {
  return new Promise((resolve) => {
    const proc = spawn("/bin/bash", ["-c", command], {
      cwd,
      env: { ...process.env, ...env },
      stdio: ["pipe", "pipe", "pipe"],
    });

    let stdout = "";
    let stderr = "";
    let timedOut = false;

    const timer = setTimeout(() => {
      timedOut = true;
      proc.kill("SIGTERM");
      setTimeout(() => proc.kill("SIGKILL"), 2000);
    }, timeoutMs);

    proc.stdout.on("data", (data: Buffer) => {
      if (stdout.length < MAX_OUTPUT_BYTES) {
        stdout += data.toString();
      }
    });

    proc.stderr.on("data", (data: Buffer) => {
      if (stderr.length < MAX_OUTPUT_BYTES) {
        stderr += data.toString();
      }
    });

    proc.on("close", (code) => {
      clearTimeout(timer);
      if (stdout.length > MAX_OUTPUT_BYTES) {
        stdout = stdout.slice(0, MAX_OUTPUT_BYTES) + "\n... [output truncated]";
      }
      if (stderr.length > MAX_OUTPUT_BYTES) {
        stderr = stderr.slice(0, MAX_OUTPUT_BYTES) + "\n... [output truncated]";
      }
      resolve({ stdout, stderr, exitCode: code, timedOut });
    });

    proc.on("error", (err) => {
      clearTimeout(timer);
      resolve({
        stdout: "",
        stderr: `Process error: ${err.message}`,
        exitCode: -1,
        timedOut: false,
      });
    });
  });
}

// executeCommand tool: run a shell command and return its output
server.tool(
  "executeCommand",
  "Execute a shell command and return its output",
  {
    command: z.string().describe("Shell command to execute"),
    cwd: z
      .string()
      .optional()
      .describe("Working directory (default /root)"),
    env: z
      .string()
      .optional()
      .describe("Additional env vars as KEY=VALUE pairs separated by newlines"),
    timeout: z
      .number()
      .optional()
      .describe("Timeout in seconds (default 30)"),
  },
  async ({ command, cwd, env, timeout }) => {
    try {
      const workDir = (cwd as string) || "/root";
      const timeoutMs = ((timeout as number) || 30) * 1000;

      const envMap: Record<string, string> = {};
      if (env) {
        for (const line of (env as string).split("\n")) {
          const idx = line.indexOf("=");
          if (idx > 0) {
            envMap[line.slice(0, idx)] = line.slice(idx + 1);
          }
        }
      }

      const result = await runCommand(
        command as string,
        workDir,
        envMap,
        Math.min(timeoutMs, DEFAULT_TIMEOUT_MS * 10)
      );

      const parts: string[] = [];
      if (result.stdout) parts.push(result.stdout);
      if (result.stderr) parts.push(`[stderr]\n${result.stderr}`);
      parts.push(
        `[exit code: ${result.exitCode}${result.timedOut ? ", timed out" : ""}]`
      );

      return {
        content: [{ type: "text", text: parts.join("\n") }],
      };
    } catch (err: any) {
      return {
        content: [{ type: "text", text: `Error: ${err.message}` }],
        isError: true,
      };
    }
  }
);

// getOutput tool: check process status by PID
server.tool(
  "getOutput",
  "Check the status of a process by PID (uses ps)",
  {
    pid: z.number().describe("Process ID to check"),
  },
  async ({ pid }) => {
    try {
      const pidNum = pid as number;
      const result = await runCommand(
        `ps -p ${pidNum} -o pid,state,cmd --no-headers 2>/dev/null || echo "Process ${pidNum} not found"`,
        "/",
        {},
        5000
      );

      return {
        content: [{ type: "text", text: result.stdout || result.stderr }],
      };
    } catch (err: any) {
      return {
        content: [{ type: "text", text: `Error: ${err.message}` }],
        isError: true,
      };
    }
  }
);

const app = express();
app.use(express.json());

const transport = new StreamableHTTPServerTransport({
  sessionIdGenerator: undefined,
});

app.post("/mcp", async (req, res) => {
  try {
    await transport.handleRequest(req, res);
  } catch (err: any) {
    console.error("MCP request error:", err);
    if (!res.headersSent) {
      res.status(500).json({ error: err.message || "Internal server error" });
    }
  }
});

app.get("/mcp", async (_req, res) => {
  res.writeHead(405).end("Method Not Allowed: Use POST for MCP requests");
});

app.delete("/mcp", async (_req, res) => {
  res.writeHead(405).end("Method Not Allowed");
});

app.get("/health", (_req, res) => {
  res.json({ status: "ok", server: "omnimaster-terminal", version: "1.0.0" });
});

// Global error handler for unhandled Express errors
app.use(
  (err: any, _req: express.Request, res: express.Response, _next: express.NextFunction) => {
    console.error("Unhandled Express error:", err);
    res.status(500).json({ error: "Internal server error" });
  }
);

async function main() {
  await server.connect(transport);
  app.listen(PORT, "127.0.0.1", () => {
    console.log(
      `omnimaster-terminal MCP server listening on http://127.0.0.1:${PORT}/mcp`
    );
  });
}

main().catch(console.error);

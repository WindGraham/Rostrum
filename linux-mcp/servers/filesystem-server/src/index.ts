import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/streamableHttp.js";
import express from "express";
import * as fs from "fs/promises";
import * as path from "path";
import { glob } from "glob";
import { z } from "zod";

const PORT = parseInt(process.env.PORT || "18700", 10);

const server = new McpServer({
  name: "omnimaster-filesystem",
  version: "1.0.0",
});

// listDir tool: list files and directories
server.tool(
  "listDir",
  "List files and directories at the given path",
  {
    path: z.string().describe("Absolute directory path"),
    recursive: z
      .boolean()
      .optional()
      .describe("Whether to list recursively (default false)"),
  },
  async ({ path: dirPath, recursive }) => {
    try {
      const targetPath = dirPath as string;
      const isRecursive = recursive === true;

      const stats = await fs.stat(targetPath);
      if (!stats.isDirectory()) {
        return {
          content: [
            {
              type: "text",
              text: `Error: ${targetPath} is not a directory`,
            },
          ],
        };
      }

      if (isRecursive) {
        const pattern = path.join(targetPath, "**/*");
        const files = await glob(pattern, { dot: true });
        const entries = await Promise.all(
          files.slice(0, 500).map(async (f) => {
            const s = await fs.stat(f).catch(() => null);
            return s
              ? `${s.isDirectory() ? "d" : "-"} ${s.size.toString().padStart(10)} ${f}`
              : `? ${f}`;
          })
        );
        return { content: [{ type: "text", text: entries.join("\n") }] };
      }

      const entries = await fs.readdir(targetPath, { withFileTypes: true });
      const lines = await Promise.all(
        entries.map(async (e) => {
          const fullPath = path.join(targetPath, e.name);
          const s = await fs.stat(fullPath).catch(() => null);
          const size = s ? s.size.toString().padStart(10) : "?".padStart(10);
          const type = e.isDirectory() ? "d" : e.isSymbolicLink() ? "l" : "-";
          return `${type} ${size} ${e.name}`;
        })
      );
      return { content: [{ type: "text", text: lines.join("\n") }] };
    } catch (err: any) {
      return {
        content: [{ type: "text", text: `Error: ${err.message}` }],
        isError: true,
      };
    }
  }
);

// readFile tool: read file contents
server.tool(
  "readFile",
  "Read the contents of a file",
  {
    path: z.string().describe("Absolute file path"),
    encoding: z
      .string()
      .optional()
      .describe("File encoding (default utf-8)"),
  },
  async ({ path: filePath, encoding }) => {
    try {
      const enc = (encoding as string) || "utf-8";
      const content = await fs.readFile(filePath as string, enc as BufferEncoding);
      return { content: [{ type: "text", text: content }] };
    } catch (err: any) {
      return {
        content: [{ type: "text", text: `Error: ${err.message}` }],
        isError: true,
      };
    }
  }
);

// writeFile tool: write content to a file
server.tool(
  "writeFile",
  "Write content to a file (creates parent directories if needed)",
  {
    path: z.string().describe("Absolute file path"),
    content: z.string().describe("Content to write"),
    append: z
      .boolean()
      .optional()
      .describe("Append instead of overwrite (default false)"),
  },
  async ({ path: filePath, content, append }) => {
    try {
      const target = filePath as string;
      await fs.mkdir(path.dirname(target), { recursive: true });
      if (append === true) {
        await fs.appendFile(target, content as string);
      } else {
        await fs.writeFile(target, content as string);
      }
      const stat = await fs.stat(target);
      return {
        content: [
          { type: "text", text: `Written ${stat.size} bytes to ${target}` },
        ],
      };
    } catch (err: any) {
      return {
        content: [{ type: "text", text: `Error: ${err.message}` }],
        isError: true,
      };
    }
  }
);

// copyFile tool: copy a file or directory
// Note: fs.cp requires Node.js >= 16.7.0. Ubuntu 24.04 ships Node 18, so this is safe.
server.tool(
  "copyFile",
  "Copy a file or directory",
  {
    source: z.string().describe("Source path"),
    destination: z.string().describe("Destination path"),
  },
  async ({ source, destination }) => {
    try {
      const src = source as string;
      const dst = destination as string;
      await fs.mkdir(path.dirname(dst), { recursive: true });
      const stat = await fs.stat(src);
      if (stat.isDirectory()) {
        await fs.cp(src, dst, { recursive: true });
      } else {
        await fs.copyFile(src, dst);
      }
      return {
        content: [{ type: "text", text: `Copied ${src} -> ${dst}` }],
      };
    } catch (err: any) {
      return {
        content: [{ type: "text", text: `Error: ${err.message}` }],
        isError: true,
      };
    }
  }
);

// moveFile tool: move or rename a file or directory
server.tool(
  "moveFile",
  "Move or rename a file or directory",
  {
    source: z.string().describe("Source path"),
    destination: z.string().describe("Destination path"),
  },
  async ({ source, destination }) => {
    try {
      const src = source as string;
      const dst = destination as string;
      await fs.mkdir(path.dirname(dst), { recursive: true });
      await fs.rename(src, dst);
      return {
        content: [{ type: "text", text: `Moved ${src} -> ${dst}` }],
      };
    } catch (err: any) {
      return {
        content: [{ type: "text", text: `Error: ${err.message}` }],
        isError: true,
      };
    }
  }
);

// deleteFile tool: delete a file or directory
server.tool(
  "deleteFile",
  "Delete a file or directory",
  {
    path: z.string().describe("Path to delete"),
    recursive: z
      .boolean()
      .optional()
      .describe("Recursively delete directories (default false)"),
  },
  async ({ path: targetPath, recursive }) => {
    try {
      const target = targetPath as string;
      let stat;
      try {
        stat = await fs.stat(target);
      } catch (err: any) {
        return {
          content: [
            { type: "text", text: `Error: ${target} does not exist` },
          ],
          isError: true,
        };
      }
      if (stat.isDirectory()) {
        if (recursive !== true) {
          return {
            content: [
              {
                type: "text",
                text: `Error: ${target} is a directory, set recursive=true to delete`,
              },
            ],
            isError: true,
          };
        }
        await fs.rm(target, { recursive: true });
      } else {
        await fs.unlink(target);
      }
      return {
        content: [{ type: "text", text: `Deleted ${target}` }],
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
  res.json({ status: "ok", server: "omnimaster-filesystem", version: "1.0.0" });
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
      `omnimaster-filesystem MCP server listening on http://127.0.0.1:${PORT}/mcp`
    );
  });
}

main().catch(console.error);

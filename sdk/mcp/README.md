# CRANIAL Model Context Protocol (MCP) Server

Official Model Context Protocol (MCP) integration for **CRANIAL — Universal AI Integration Platform**.

Exposes CRANIAL's safe, read-only tools and RAG knowledge retrieval to MCP-compatible AI editors and agents (Cursor, Claude Desktop, Antigravity, etc.).

## Features

- **Strict Tenant & Application Isolation:** Requests run through CRANIAL's authenticated integration plane.
- **Read-Only Safety:** AI tools inspect reports and catalog data without database mutation.
- **Strict Money Management Boundary:** CRANIAL is strictly an integration layer for money management and does **not** perform banking or money transfers. Any transfer attempt is rejected immediately.

## Configuration for Claude Desktop / Cursor

Add to your `claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "cranial": {
      "command": "node",
      "args": ["/absolute/path/to/cranial/sdk/mcp/cranial-mcp-server.js"],
      "env": {
        "CRANIAL_BASE_URL": "http://localhost:8080",
        "CRANIAL_API_KEY": "crn_dev_sms_app001.secret_sandbox_sms_key_9901"
      }
    }
  }
}
```

## Available MCP Tools

- `sms_get_daily_sales`: Query store sales revenue and transaction metrics.
- `sms_get_low_stock_items`: List inventory items below reorder thresholds.
- `worker_get_assigned_tasks`: Inquire assigned logistics tasks for workers.
- `peggy_get_budget_breakdown`: Retrieve 50-30-20 personal budget allocation status (money management, non-banking).
- `peggy_get_monthly_spending`: Retrieve categorized expenses for spending pattern analysis.
- `ecom_get_product_info`: Inquire product specifications, price, and stock status.
- `cranial_ask_knowledge`: Search and answer questions from CRANIAL RAG collections with citations.


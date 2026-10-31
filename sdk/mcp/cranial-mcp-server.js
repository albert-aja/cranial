#!/usr/bin/env node

/**
 * CRANIAL Model Context Protocol (MCP) Server (JSON-RPC 2.0 stdio)
 * Exposes authorized, read-only tools and knowledge collections to MCP-compatible AI clients
 * (Cursor, Claude Desktop, Antigravity, etc.) while enforcing CRANIAL's strict boundaries:
 * - Strict tenant isolation
 * - Read-only tool execution
 * - Non-negotiable money management boundary: zero banking or payment transfers allowed
 */

import readline from 'node:readline';

const BASE_URL = process.env.CRANIAL_BASE_URL || 'http://localhost:8080';
const API_KEY = process.env.CRANIAL_API_KEY || 'crn_dev_sms_app001.secret_sandbox_sms_key_9901';

const TOOLS = [
  {
    name: 'sms_get_daily_sales',
    description: 'Retrieve store gross/net sales, transaction count, and category breakdown for store owners.',
    inputSchema: {
      type: 'object',
      properties: {
        date: { type: 'string', description: 'Date in YYYY-MM-DD format (defaults to today)' },
      },
    },
  },
  {
    name: 'sms_get_low_stock_items',
    description: 'Retrieve items in inventory that have fallen below minimum reorder thresholds.',
    inputSchema: {
      type: 'object',
      properties: {
        warehouse_ref: { type: 'string', description: 'Warehouse identifier (e.g. WH-JKT-01)' },
      },
    },
  },
  {
    name: 'worker_get_assigned_tasks',
    description: 'Inquire assigned pickup, delivery, and packing tasks for workers.',
    inputSchema: {
      type: 'object',
      properties: {
        worker_ref: { type: 'string', description: 'Worker email or employee identifier' },
      },
    },
  },
  {
    name: 'peggy_get_budget_breakdown',
    description: 'Retrieve 50-30-20 budget allocation status and remaining spending allowances in Peggy. Purely money management, strictly non-banking (no fund transfers).',
    inputSchema: {
      type: 'object',
      properties: {
        user_ref: { type: 'string', description: 'Peggy user reference' },
      },
    },
  },
  {
    name: 'peggy_get_monthly_spending',
    description: 'Retrieve categorized expenses for money management spending intelligence. Purely non-banking.',
    inputSchema: {
      type: 'object',
      properties: {
        period: { type: 'string', description: 'Month in YYYY-MM format' },
      },
    },
  },
  {
    name: 'ecom_get_product_info',
    description: 'Retrieve product specifications, price, and stock levels from online catalog.',
    inputSchema: {
      type: 'object',
      properties: {
        sku: { type: 'string', description: 'Product SKU (e.g. ECOM-CHAIR-01)' },
      },
      required: ['sku'],
    },
  },
  {
    name: 'cranial_ask_knowledge',
    description: 'Ask a grounded natural language question across authorized CRANIAL RAG knowledge collections.',
    inputSchema: {
      type: 'object',
      properties: {
        collection_code: { type: 'string', description: 'Collection code (e.g. peggy_financial_encyclopedia_ref or ops_sop_handbook_ref)' },
        question: { type: 'string', description: 'Question to ask' },
      },
      required: ['collection_code', 'question'],
    },
  },
];

function sendJsonRpc(response) {
  process.stdout.write(JSON.stringify(response) + '\n');
}

async function handleToolCall(name, args) {
  // Safety check: CRANIAL is strictly a money management platform, never banking or payment execution
  const nameLower = name.toLowerCase();
  if (
    nameLower.includes('payment') ||
    nameLower.includes('transfer') ||
    nameLower.includes('disburse') ||
    nameLower.includes('withdraw')
  ) {
    throw new Error('Tool execution prohibited: CRANIAL is strictly a money management platform and does not handle payments or bank transfers.');
  }

  if (name === 'cranial_ask_knowledge') {
    const res = await fetch(`${BASE_URL}/api/v1/knowledge/ask`, {
      method: 'POST',
      headers: {
        'X-Cranial-Api-Key': API_KEY,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        collection_code: args.collection_code,
        question: args.question,
      }),
    });
    const json = await res.json();
    return json.data || json;
  }

  // Execute task or tool inquiry
  const res = await fetch(`${BASE_URL}/api/v1/orchestrator/execute`, {
    method: 'POST',
    headers: {
      'X-Cranial-Api-Key': API_KEY,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      task_code: 'generic_assistance',
      inputs: {
        context: `Tool call requested: ${name} with arguments: ${JSON.stringify(args)}`,
        query: `Execute tool ${name}`,
      },
      allow_tools: true,
    }),
  });

  const json = await res.json();
  return json.data || json;
}

const rl = readline.createInterface({
  input: process.stdin,
  output: process.stdout,
  terminal: false,
});

rl.on('line', async (line) => {
  if (!line.trim()) return;

  let request;
  try {
    request = JSON.parse(line);
  } catch (err) {
    sendJsonRpc({
      jsonrpc: '2.0',
      id: null,
      error: { code: -32700, message: 'Parse error: invalid JSON' },
    });
    return;
  }

  const { id, method, params } = request;

  if (method === 'initialize') {
    sendJsonRpc({
      jsonrpc: '2.0',
      id,
      result: {
        protocolVersion: '2024-11-05',
        capabilities: {
          tools: {},
        },
        serverInfo: {
          name: 'cranial-mcp-server',
          version: '0.1.0',
        },
      },
    });
    return;
  }

  if (method === 'notifications/initialized') {
    return;
  }

  if (method === 'tools/list') {
    sendJsonRpc({
      jsonrpc: '2.0',
      id,
      result: {
        tools: TOOLS,
      },
    });
    return;
  }

  if (method === 'tools/call') {
    const { name, arguments: args } = params || {};
    try {
      const toolResult = await handleToolCall(name, args || {});
      sendJsonRpc({
        jsonrpc: '2.0',
        id,
        result: {
          content: [
            {
              type: 'text',
              text: JSON.stringify(toolResult, null, 2),
            },
          ],
        },
      });
    } catch (err) {
      sendJsonRpc({
        jsonrpc: '2.0',
        id,
        result: {
          content: [
            {
              type: 'text',
              text: `Error executing tool '${name}': ${err.message}`,
            },
          ],
          isError: true,
        },
      });
    }
    return;
  }

  sendJsonRpc({
    jsonrpc: '2.0',
    id,
    error: { code: -32601, message: `Method not found: ${method}` },
  });
});


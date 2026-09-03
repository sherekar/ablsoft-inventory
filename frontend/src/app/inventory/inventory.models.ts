export interface InventoryItem {
  id: number;
  sku: string;
  productName: string;
  category: string;
  purchaseDate: string;
  unitPrice: number;
  quantity: number;
  stockAgeDays: number;
}

export interface InventoryPage {
  content: InventoryItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface InventorySummary {
  totalProducts: number;
  totalEntries: number;
  totalInventoryValue: number;
  averageStockAgeDays: number;
  currency: string;
  asOfDate: string;
}

export interface ImportResult { importedRows: number; message: string; }
export interface RowError { row: number; column: string; message: string; }
export interface ApiProblem { title: string; detail: string; errors?: RowError[]; totalErrors?: number; }
export interface TableQuery { page: number; size: number; sort: string; direction: 'asc' | 'desc'; }

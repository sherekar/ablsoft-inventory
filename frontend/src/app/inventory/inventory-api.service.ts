import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { ApiProblem, ImportResult, InventoryPage, InventorySummary, TableQuery } from './inventory.models';

@Injectable({ providedIn: 'root' })
export class InventoryApiService {
  private readonly http = inject(HttpClient);

  list(query: TableQuery) {
    return this.http.get<InventoryPage>('/api/inventory', { params: { ...query } });
  }

  summary() { return this.http.get<InventorySummary>('/api/inventory/summary'); }

  import(file: File) {
    const body = new FormData();
    body.append('file', file);
    return this.http.post<ImportResult>('/api/inventory/imports', body);
  }
}

export function toProblem(error: unknown): ApiProblem {
  if (error instanceof HttpErrorResponse) {
    const body: unknown = error.error;
    if (body && typeof body === 'object' && 'detail' in body && typeof body.detail === 'string') {
      return body as ApiProblem;
    }
    if (error.status === 413) return { title: 'File too large', detail: 'The maximum workbook size is 5 MB.' };
    if (error.status === 0) return { title: 'Connection interrupted', detail: 'Check your connection and try again.' };
  }
  return { title: 'Request failed', detail: 'The request could not be completed. Please try again.' };
}

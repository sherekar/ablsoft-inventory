import { TestBed } from '@angular/core/testing';
import { provideHttpClient, HttpErrorResponse } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { InventoryApiService, toProblem } from './inventory-api.service';

describe('InventoryApiService', () => {
  let api: InventoryApiService;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(InventoryApiService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('sends server pagination and sorting parameters', () => {
    api.list({ page: 2, size: 25, sort: 'stockAgeDays', direction: 'desc' }).subscribe();
    const request = http.expectOne(r => r.url === '/api/inventory');
    expect(request.request.params.get('page')).toBe('2');
    expect(request.request.params.get('size')).toBe('25');
    expect(request.request.params.get('sort')).toBe('stockAgeDays');
    expect(request.request.params.get('direction')).toBe('desc');
    request.flush({ content: [], totalElements: 0 });
  });

  it('uploads the actual file as multipart data without a manual content-type', () => {
    const file = new File(['xlsx content'], 'inventory.xlsx');
    api.import(file).subscribe();
    const request = http.expectOne('/api/inventory/imports');
    expect(request.request.method).toBe('POST');
    expect(request.request.body.get('file')).toBe(file);
    expect(request.request.headers.has('Content-Type')).toBe(false);
    request.flush({ importedRows: 1, message: 'Done' });
  });

  it('preserves row validation errors and handles non-JSON proxy errors', () => {
    const problem = { title: 'Invalid import', detail: 'No rows saved', errors: [{ row: 2, column: 'Quantity', message: 'Required' }] };
    expect(toProblem(new HttpErrorResponse({ status: 422, error: problem }))).toEqual(problem);
    expect(toProblem(new HttpErrorResponse({ status: 413, error: '<html>Too large</html>' })).title).toBe('File too large');
    expect(toProblem(new HttpErrorResponse({ status: 0 })).title).toBe('Connection interrupted');
  });
});

import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { DashboardComponent } from './dashboard.component';

describe('DashboardComponent', () => {
  let fixture: ComponentFixture<DashboardComponent>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [DashboardComponent], providers: [provideHttpClient(), provideHttpClientTesting()] }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();
  });
  afterEach(() => http.verify());

  function respond() {
    http.expectOne(r => r.url === '/api/inventory').flush({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 });
    http.expectOne('/api/inventory/summary').flush({ totalProducts: 0, totalEntries: 0, totalInventoryValue: 0, averageStockAgeDays: 0, currency: 'USD', asOfDate: '2026-09-03' });
    fixture.detectChanges();
  }

  it('shows a useful empty state after loading', () => {
    respond();
    expect(fixture.nativeElement.textContent).toContain('Your inventory starts here');
    expect(fixture.componentInstance.loading()).toBe(false);
  });

  it('resets the page when sorting and sends the new sort to the API', () => {
    respond();
    fixture.componentInstance.query.page = 3;
    fixture.componentInstance.sortChanged({ active: 'unitPrice', direction: 'asc' });
    const request = http.expectOne(r => r.url === '/api/inventory');
    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.get('sort')).toBe('unitPrice');
    request.flush({ content: [], totalElements: 0 });
    http.expectOne('/api/inventory/summary').flush({ totalProducts: 0 });
  });
});

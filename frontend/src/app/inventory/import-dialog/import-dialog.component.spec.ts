import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { MatDialogRef } from '@angular/material/dialog';
import { ImportDialogComponent } from './import-dialog.component';

describe('ImportDialogComponent', () => {
  let fixture: ComponentFixture<ImportDialogComponent>;
  let http: HttpTestingController;
  const dialog = { close: vi.fn(), disableClose: false };

  beforeEach(async () => {
    dialog.close.mockClear();
    dialog.disableClose = false;
    await TestBed.configureTestingModule({ imports: [ImportDialogComponent], providers: [
      provideHttpClient(), provideHttpClientTesting(), { provide: MatDialogRef, useValue: dialog }
    ] }).compileComponents();
    fixture = TestBed.createComponent(ImportDialogComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });
  afterEach(() => http.verify());

  function select(file: File) {
    const input = fixture.nativeElement.querySelector('input');
    Object.defineProperty(input, 'files', { value: [file], configurable: true });
    input.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  it('rejects incorrect extensions and oversized workbooks before upload', () => {
    select(new File(['data'], 'inventory.csv'));
    expect(fixture.componentInstance.file()).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Select an .xlsx');
    select(new File([new Uint8Array(5 * 1024 * 1024 + 1)], 'large.xlsx'));
    expect(fixture.componentInstance.file()).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('maximum workbook size');
  });

  it('shows row errors and permits correction after a rejected import', () => {
    select(new File(['data'], 'inventory.xlsx'));
    fixture.componentInstance.upload();
    expect(dialog.disableClose).toBe(true);
    http.expectOne('/api/inventory/imports').flush({ title: 'Import validation failed', detail: 'No rows were saved.',
      errors: [{ row: 8, column: 'Quantity', message: 'Use a whole number.' }], totalErrors: 1
    }, { status: 422, statusText: 'Unprocessable Entity' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Use a whole number.');
    expect(fixture.componentInstance.uploading()).toBe(false);
    expect(dialog.disableClose).toBe(false);
    expect(dialog.close).not.toHaveBeenCalled();
  });

  it('prevents duplicate submissions and closes only after a successful import', () => {
    select(new File(['data'], 'inventory.xlsx'));
    fixture.componentInstance.upload();
    fixture.componentInstance.upload();
    const result = { importedRows: 3, message: '3 rows added' };
    http.expectOne('/api/inventory/imports').flush(result);
    expect(dialog.close).toHaveBeenCalledExactlyOnceWith(result);
  });
});

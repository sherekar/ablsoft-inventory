import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { catchError, EMPTY, finalize, forkJoin, startWith, Subject, switchMap } from 'rxjs';
import { InventoryApiService, toProblem } from '../inventory-api.service';
import { ApiProblem, ImportResult, InventoryPage, InventorySummary, TableQuery } from '../inventory.models';
import { ImportDialogComponent } from '../import-dialog/import-dialog.component';
import { DateOnlyPipe } from '../date-only.pipe';

@Component({
  selector: 'app-root',
  imports: [CurrencyPipe, DateOnlyPipe, DecimalPipe, MatButtonModule, MatPaginatorModule,
    MatProgressBarModule, MatSortModule, MatTableModule],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss'
})
export class DashboardComponent {
  private readonly api = inject(InventoryApiService);
  private readonly dialog = inject(MatDialog);
  private readonly reload$ = new Subject<void>();
  readonly columns = ['sku', 'productName', 'category', 'purchaseDate', 'unitPrice', 'quantity', 'stockAgeDays'];
  readonly page = signal<InventoryPage | null>(null);
  readonly summary = signal<InventorySummary | null>(null);
  readonly loading = signal(true);
  readonly error = signal<ApiProblem | null>(null);
  readonly notice = signal('');
  query: TableQuery = { page: 0, size: 10, sort: 'purchaseDate', direction: 'desc' };

  constructor() {
    this.reload$.pipe(startWith(undefined), switchMap(() => {
      this.loading.set(true);
      this.error.set(null);
      return forkJoin({ page: this.api.list(this.query), summary: this.api.summary() }).pipe(
        catchError(error => {
          this.error.set(toProblem(error));
          this.page.set(null);
          this.summary.set(null);
          return EMPTY;
        }),
        finalize(() => this.loading.set(false))
      );
    }), takeUntilDestroyed()).subscribe(result => {
      this.page.set(result.page);
      this.summary.set(result.summary);
    });
  }

  reload() { this.reload$.next(); }

  sortChanged(sort: Sort) {
    this.query = { ...this.query, page: 0, sort: sort.active, direction: sort.direction === 'asc' ? 'asc' : 'desc' };
    this.reload();
  }

  pageChanged(event: PageEvent) {
    this.query = { ...this.query, page: event.pageIndex, size: event.pageSize };
    this.reload();
  }

  openImport() {
    this.dialog.open<ImportDialogComponent, undefined, ImportResult>(ImportDialogComponent, {
      width: '620px', maxWidth: 'calc(100vw - 32px)', autoFocus: 'first-tabbable', restoreFocus: true
    }).afterClosed().subscribe(result => {
      if (!result) return;
      this.notice.set(result.message);
      this.query = { ...this.query, page: 0 };
      this.reload();
    });
  }
}

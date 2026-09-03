import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { finalize } from 'rxjs';
import { InventoryApiService, toProblem } from '../inventory-api.service';
import { ApiProblem, ImportResult } from '../inventory.models';
import { DestroyRef } from '@angular/core';

@Component({
  selector: 'app-import-dialog',
  imports: [MatButtonModule, MatDialogModule, MatProgressBarModule],
  templateUrl: './import-dialog.component.html',
  styleUrl: './import-dialog.component.scss'
})
export class ImportDialogComponent {
  private readonly api = inject(InventoryApiService);
  private readonly dialog = inject(MatDialogRef<ImportDialogComponent, ImportResult>);
  private readonly destroyRef = inject(DestroyRef);
  readonly file = signal<File | null>(null);
  readonly uploading = signal(false);
  readonly problem = signal<ApiProblem | null>(null);

  selectFile(event: Event) {
    const file = (event.target as HTMLInputElement).files?.[0] ?? null;
    this.problem.set(null);
    this.file.set(null);
    if (!file) return;
    if (!file.name.toLowerCase().endsWith('.xlsx')) {
      this.problem.set({ title: 'Unsupported file', detail: 'Select an .xlsx workbook.' });
    } else if (file.size > 5 * 1024 * 1024) {
      this.problem.set({ title: 'File too large', detail: 'The maximum workbook size is 5 MB.' });
    } else if (file.size === 0) {
      this.problem.set({ title: 'Empty file', detail: 'Select a workbook containing inventory rows.' });
    } else {
      this.file.set(file);
    }
  }

  upload() {
    const file = this.file();
    if (!file || this.uploading()) return;
    this.uploading.set(true);
    this.problem.set(null);
    this.dialog.disableClose = true;
    this.api.import(file).pipe(takeUntilDestroyed(this.destroyRef), finalize(() => {
      this.uploading.set(false);
      this.dialog.disableClose = false;
    })).subscribe({ next: result => this.dialog.close(result), error: error => this.problem.set(toProblem(error)) });
  }
}

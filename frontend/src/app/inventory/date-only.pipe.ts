import { formatDate } from '@angular/common';
import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'dateOnly' })
export class DateOnlyPipe implements PipeTransform {
  transform(value: string): string {
    // These API values are calendar dates. Anchor them to UTC before formatting
    // so the browser's timezone cannot shift a purchase date to the previous day.
    return formatDate(`${value}T00:00:00Z`, 'dd MMM yyyy', 'en-US', 'UTC');
  }
}

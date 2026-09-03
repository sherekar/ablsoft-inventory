import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { DashboardComponent } from './app/inventory/dashboard/dashboard.component';

bootstrapApplication(DashboardComponent, appConfig).catch(console.error);

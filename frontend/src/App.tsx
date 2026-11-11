import { useState } from 'react';
import { Navbar } from './components/layout/Navbar';
import { Sidebar, type NavTab } from './components/layout/Sidebar';
import { DashboardView } from './components/views/DashboardView';
import { CatalogView } from './components/views/CatalogView';
import { InventoryView } from './components/views/InventoryView';
import { OrdersView } from './components/views/OrdersView';
import { MetricsView } from './components/views/MetricsView';

export function App() {
  const [activeTab, setActiveTab] = useState<NavTab>('dashboard');

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col font-sans antialiased selection:bg-amber-500/30 selection:text-amber-200">
      <Navbar />

      <div className="flex flex-1 overflow-hidden">
        <Sidebar activeTab={activeTab} onTabChange={setActiveTab} />

        <main className="flex-1 overflow-y-auto p-6 md:p-8 bg-gradient-to-b from-slate-950 via-slate-950 to-slate-900/50">
          <div className="max-w-7xl mx-auto">
            {activeTab === 'dashboard' && (
              <DashboardView onNavigateToOrders={() => setActiveTab('orders')} />
            )}
            {activeTab === 'catalog' && <CatalogView />}
            {activeTab === 'inventory' && <InventoryView />}
            {activeTab === 'orders' && <OrdersView />}
            {activeTab === 'metrics' && <MetricsView />}
          </div>
        </main>
      </div>
    </div>
  );
}

export default App;

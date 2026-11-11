import { requestJson } from './client';
import type { Category, PageResponse, Product, ProductCreateRequest } from '../types/catalog';

export const catalogApi = {
  getProducts: (page = 0, size = 15, search?: string, categoryId?: number) => {
    const params = new URLSearchParams({
      page: page.toString(),
      size: size.toString(),
      sort: 'createdAt,desc',
    });
    if (search) params.set('search', search);
    if (categoryId) params.set('categoryId', categoryId.toString());

    return requestJson<PageResponse<Product>>(`/products?${params.toString()}`);
  },

  getProductById: (id: number) => {
    return requestJson<Product>(`/products/${id}`);
  },

  createProduct: (data: ProductCreateRequest) => {
    return requestJson<Product>('/products', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  },

  getCategories: () => {
    return requestJson<Category[]>('/categories');
  },
};

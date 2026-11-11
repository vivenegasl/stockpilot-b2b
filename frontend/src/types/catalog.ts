export interface Category {
  id: number;
  name: string;
  description: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface Product {
  id: number;
  sku: string;
  name: string;
  description: string;
  unitPrice: number;
  category: Category;
  active: boolean;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface ProductCreateRequest {
  sku: string;
  name: string;
  description: string;
  unitPrice: number;
  categoryId: number;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
}

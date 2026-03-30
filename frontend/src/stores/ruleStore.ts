import { create } from 'zustand';
import type { Rule, RuleQueryParams } from '../types/rule';
import * as rulesApi from '../api/rules';

interface RuleStore {
  rules: Rule[];
  total: number;
  loading: boolean;
  currentParams: RuleQueryParams;
  fetchRules: (params?: RuleQueryParams) => Promise<void>;
  deleteRule: (ruleKey: string) => Promise<void>;
  toggleEnabled: (ruleKey: string, enabled: boolean) => Promise<void>;
}

export const useRuleStore = create<RuleStore>((set, get) => ({
  rules: [],
  total: 0,
  loading: false,
  currentParams: { page: 0, size: 20 },

  fetchRules: async (params?: RuleQueryParams) => {
    const mergedParams = { ...get().currentParams, ...params };
    set({ loading: true, currentParams: mergedParams });
    try {
      const hasFilters = mergedParams.status || mergedParams.keyword !== undefined;
      const data = hasFilters
        ? await rulesApi.queryRules(mergedParams)
        : await rulesApi.getRules(mergedParams);
      set({ rules: data.content, total: data.totalElements });
    } catch (error) {
      console.error('Failed to fetch rules:', error);
      set({ rules: [], total: 0 });
    } finally {
      set({ loading: false });
    }
  },

  deleteRule: async (ruleKey: string) => {
    await rulesApi.deleteRule(ruleKey);
    await get().fetchRules();
  },

  toggleEnabled: async (ruleKey: string, enabled: boolean) => {
    if (enabled) {
      await rulesApi.enableRule(ruleKey);
    } else {
      await rulesApi.disableRule(ruleKey);
    }
    await get().fetchRules();
  },
}));

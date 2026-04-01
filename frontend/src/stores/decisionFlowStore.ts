import { create } from 'zustand';
import type { DecisionFlow, DecisionFlowQueryParams } from '../types/decisionFlow';
import * as decisionFlowApi from '../api/decisionFlows';

interface DecisionFlowStore {
  flows: DecisionFlow[];
  total: number;
  loading: boolean;
  currentParams: DecisionFlowQueryParams;
  fetchFlows: (params?: DecisionFlowQueryParams) => Promise<void>;
  deleteFlow: (flowKey: string) => Promise<void>;
  toggleEnabled: (flowKey: string, enabled: boolean) => Promise<void>;
}

export const useDecisionFlowStore = create<DecisionFlowStore>((set, get) => ({
  flows: [],
  total: 0,
  loading: false,
  currentParams: { page: 0, size: 20 },

  fetchFlows: async (params?: DecisionFlowQueryParams) => {
    const mergedParams = { ...get().currentParams, ...params };
    set({ loading: true, currentParams: mergedParams });
    try {
      const hasFilters = mergedParams.status || mergedParams.keyword !== undefined;
      const data = hasFilters
        ? await decisionFlowApi.queryDecisionFlows(mergedParams)
        : await decisionFlowApi.getDecisionFlows(mergedParams);
      set({ flows: data.content, total: data.totalElements });
    } catch (error) {
      console.error('Failed to fetch decision flows:', error);
      set({ flows: [], total: 0 });
    } finally {
      set({ loading: false });
    }
  },

  deleteFlow: async (flowKey: string) => {
    await decisionFlowApi.deleteDecisionFlow(flowKey);
    await get().fetchFlows();
  },

  toggleEnabled: async (flowKey: string, enabled: boolean) => {
    if (enabled) {
      await decisionFlowApi.enableDecisionFlow(flowKey);
    } else {
      await decisionFlowApi.disableDecisionFlow(flowKey);
    }
    await get().fetchFlows();
  },
}));

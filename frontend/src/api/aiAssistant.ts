import { getData, postData } from './http'
import type { AxiosRequestConfig } from 'axios'

export interface AiMessage {
  role: 'user' | 'assistant'
  text: string
  image?: string
}

export const getAiModels = () => getData<{ models: Array<{ name: string }> }>('/ai-assistant/models')
export const chatWithAi = (messages: AiMessage[], config?: AxiosRequestConfig) =>
  postData<{ model: string; text: string }>('/ai-assistant/chat', { messages }, config)

import { getData, postData } from './http'

export interface AiMessage {
  role: 'user' | 'assistant'
  text: string
  image?: string
}

export const getAiModels = () => getData<{ models: Array<{ name: string }> }>('/ai-assistant/models')
export const chatWithAi = (messages: AiMessage[]) =>
  postData<{ model: string; text: string }>('/ai-assistant/chat', { messages }, { timeout: 130000 })

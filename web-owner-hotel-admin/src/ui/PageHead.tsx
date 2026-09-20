import type { ReactNode } from 'react'
import { Flex } from 'antd'

/**
 * The title block every screen starts with.
 *
 * <p>A component rather than a copied `<Typography.Title>` so the subtitle is a
 * habit: a page that explains what it is for in one line is the difference
 * between a queue someone works and a queue someone avoids.
 */
export function PageHead({ title, description, extra }: {
  title: string
  description?: ReactNode
  extra?: ReactNode
}) {
  return (
    <Flex align="flex-start" justify="space-between" gap={16} className="page-head" wrap>
      <div>
        <h1>{title}</h1>
        {description && <p>{description}</p>}
      </div>
      {extra && <Flex gap={8} wrap>{extra}</Flex>}
    </Flex>
  )
}

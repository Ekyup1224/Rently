import { useRef, useState } from 'react'
import { Button, Card, Flex, Image, Popconfirm, Space, Tag, Typography, Upload, message } from 'antd'
import type { UploadProps } from 'antd'
import { RequestError } from '../../api/client'
import { owner, putToStorage } from '../../api/endpoints'
import type { Photo } from '../../types'

const MAX_BYTES = 10 * 1024 * 1024
const ACCEPTED = ['image/jpeg', 'image/png', 'image/webp']

/**
 * Photo gallery management.
 *
 * <p>Uploads go browser-to-bucket: the API hands out a presigned URL, the file is
 * PUT straight to storage, and only then is it registered. Large images never
 * touch the application server, and a failed PUT simply leaves nothing registered.
 */
export function PhotoManager({
  propertyId, photos, onChanged,
}: { propertyId: string; photos: Photo[]; onChanged: () => void }) {
  const [busy, setBusy] = useState(false)
  const dragged = useRef<string | null>(null)

  /** antd hands the raw request over so uploads can go straight to storage. */
  const upload: NonNullable<UploadProps['customRequest']> = async (option) => {
    const file = option.file as File
    if (!ACCEPTED.includes(file.type)) {
      message.error('Photos must be JPEG, PNG or WebP')
      option.onError?.(new Error('unsupported type'))
      return
    }
    if (file.size > MAX_BYTES) {
      message.error('Photos must be 10 MB or smaller')
      option.onError?.(new Error('too large'))
      return
    }

    setBusy(true)
    try {
      const presigned = await owner.photoUploadUrl(propertyId,
        { contentType: file.type, sizeBytes: file.size })
      await putToStorage(presigned.uploadUrl, file)
      // Registered only after storage confirms it holds the object.
      await owner.confirmPhoto(propertyId, { storageKey: presigned.storageKey, altText: file.name })
      option.onSuccess?.({})
      message.success('Photo added')
      onChanged()
    } catch (failure) {
      option.onError?.(failure as Error)
      message.error(failure instanceof RequestError
        ? failure.message : 'The upload did not complete')
    } finally {
      setBusy(false)
    }
  }

  async function reorder(nextOrder: string[], coverPhotoId?: string) {
    setBusy(true)
    try {
      await owner.reorderPhotos(propertyId, { photoIdsInOrder: nextOrder, coverPhotoId })
      onChanged()
    } catch (failure) {
      message.error(failure instanceof RequestError ? failure.message : 'Could not reorder')
    } finally {
      setBusy(false)
    }
  }

  async function remove(photoId: string) {
    setBusy(true)
    try {
      await owner.deletePhoto(propertyId, photoId)
      message.success('Photo removed')
      onChanged()
    } catch (failure) {
      message.error(failure instanceof RequestError ? failure.message : 'Could not remove')
    } finally {
      setBusy(false)
    }
  }

  /** Drops the dragged photo in front of the target, keeping everything else in order. */
  function handleDrop(targetId: string) {
    const sourceId = dragged.current
    dragged.current = null
    if (!sourceId || sourceId === targetId) {
      return
    }
    const order = photos.map((photo) => photo.id).filter((id) => id !== sourceId)
    order.splice(order.indexOf(targetId), 0, sourceId)
    reorder(order)
  }

  return (
    <Card title="Photos" size="small"
          extra={<Typography.Text type="secondary" style={{ fontSize: 12 }}>
            Drag to reorder · the cover leads search results
          </Typography.Text>}>
      <Flex gap={12} wrap>
        {photos.map((photo) => (
          <div
            key={photo.id}
            draggable
            onDragStart={() => { dragged.current = photo.id }}
            onDragOver={(event) => event.preventDefault()}
            onDrop={() => handleDrop(photo.id)}
            style={{ width: 180, cursor: 'grab' }}
          >
            <Image src={photo.url} alt={photo.altText ?? ''} width={180} height={124}
                   style={{ objectFit: 'cover', borderRadius: 8 }} />
            <Flex justify="space-between" align="center" style={{ marginTop: 4 }}>
              {photo.cover
                ? <Tag color="blue">Cover</Tag>
                : <Button type="link" size="small" disabled={busy}
                          onClick={() => reorder(photos.map((item) => item.id), photo.id)}>
                    Make cover
                  </Button>}
              <Popconfirm title="Remove this photo?" okButtonProps={{ danger: true }}
                          onConfirm={() => remove(photo.id)}>
                <Button type="text" size="small" danger disabled={busy}>Remove</Button>
              </Popconfirm>
            </Flex>
          </div>
        ))}

        <Upload.Dragger
          multiple
          showUploadList={false}
          accept={ACCEPTED.join(',')}
          customRequest={upload}
          disabled={busy}
          style={{ width: 180, height: 124 }}
        >
          <Space orientation="vertical" size={2}>
            <Typography.Text strong style={{ fontSize: 13 }}>Add photos</Typography.Text>
            <Typography.Text type="secondary" style={{ fontSize: 11 }}>
              JPEG, PNG or WebP · up to 10 MB
            </Typography.Text>
          </Space>
        </Upload.Dragger>
      </Flex>

      {photos.length === 0 && (
        <Typography.Text type="warning" style={{ fontSize: 12, display: 'block', marginTop: 8 }}>
          At least one photo is required before a listing can be submitted.
        </Typography.Text>
      )}
    </Card>
  )
}

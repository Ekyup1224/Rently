import { useState } from 'react'
import { App as AntApp, Form, Input, InputNumber, Modal, Select, Typography } from 'antd'
import { RequestError } from '../../api/client'
import { owner } from '../../api/endpoints'
import type { Property, PropertyType } from '../../types'

const PROPERTY_TYPES: { value: PropertyType; label: string }[] = [
  { value: 'APARTMENT', label: 'Apartment' },
  { value: 'HOUSE', label: 'House' },
  { value: 'GER', label: 'Ger' },
  { value: 'CABIN', label: 'Cabin' },
  { value: 'VILLA', label: 'Villa' },
  { value: 'STUDIO', label: 'Studio' },
  { value: 'TOWNHOUSE', label: 'Townhouse' },
  { value: 'GUESTHOUSE', label: 'Guesthouse' },
]

/**
 * Starts a listing with only the essentials, so the first screen can save and the
 * rest is filled in on the editor. Nothing is published until it is submitted and
 * approved.
 */
export function CreateListingModal({
  open, onClose, onCreated,
}: { open: boolean; onClose: () => void; onCreated: (created: Property) => void }) {
  // The context instance, not the static one: static message renders outside
  // AntApp's holder and gets hidden behind the app shell header.
  const { message } = AntApp.useApp()

  const [busy, setBusy] = useState(false)

  async function submit(values: {
    title: string; propertyType: PropertyType; city: string; maxGuests: number
  }) {
    setBusy(true)
    try {
      onCreated(await owner.createProperty(values))
    } catch (failure) {
      message.error(failure instanceof RequestError
        ? failure.message : 'Could not create the listing')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal
      open={open}
      title="New listing"
      onCancel={onClose}
      okText="Create draft"
      okButtonProps={{ loading: busy }}
      onOk={() => document.getElementById('create-listing-submit')?.click()}
      destroyOnHidden
    >
      <Typography.Paragraph type="secondary" style={{ marginTop: 0 }}>
        Just the basics for now — you will add photos, pricing and a description next.
      </Typography.Paragraph>
      <Form layout="vertical" onFinish={submit}
            initialValues={{ propertyType: 'APARTMENT', city: 'Ulaanbaatar', maxGuests: 2 }}>
        <Form.Item label="Title" name="title"
                   rules={[{ required: true, message: 'Give the place a name guests will see' }]}>
          <Input placeholder="Sunny apartment near Sukhbaatar Square" maxLength={150} />
        </Form.Item>
        <Form.Item label="Type" name="propertyType" rules={[{ required: true }]}>
          <Select options={PROPERTY_TYPES} />
        </Form.Item>
        <Form.Item label="City" name="city" rules={[{ required: true }]}>
          <Input placeholder="Ulaanbaatar" maxLength={120} />
        </Form.Item>
        <Form.Item label="Sleeps" name="maxGuests" rules={[{ required: true }]}>
          <InputNumber min={1} max={50} style={{ width: '100%' }} />
        </Form.Item>
        <button id="create-listing-submit" type="submit" hidden />
      </Form>
    </Modal>
  )
}

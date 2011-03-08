package com.mt.storage.conversion;

import com.mt.storage.KeyManagement;
import com.mt.storage.PersistingElement;

class PersistingConverter extends Converter<PersistingElement> {
	private final KeyManagement keyManager = KeyManagement.getInstance();
	
	public PersistingConverter() {
		super(PersistingElement.class);
	}

	@Override
	public PersistingElement fromString(String rep, Class<?> expected) {
		return (PersistingElement) keyManager.createElement(expected, rep);
	}

	@Override
	public String toString(PersistingElement obj, Class<? extends PersistingElement> expected) {
		return obj.getClass().equals(expected) ? obj.getIdentifier() : keyManager.createIdentifier(obj, expected);
	}

	@Override
	public PersistingElement fromBytes(byte[] rep, Class<?> expected) {
		return this.fromString(ConversionTools.stringConverter.fromBytes(rep, String.class), expected);
	}

	@Override
	public byte[] toBytes(PersistingElement obj, Class<? extends PersistingElement> expected) {
		return ConversionTools.stringConverter.toBytes(this.toString(obj, expected));
	}

	@Override
	public boolean canConvert(Class<?> type) {
		return this.getClazz().isAssignableFrom(type);
	}

}